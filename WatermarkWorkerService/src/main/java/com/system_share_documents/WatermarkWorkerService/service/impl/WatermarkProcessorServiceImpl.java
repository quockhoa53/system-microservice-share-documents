package com.system_share_documents.WatermarkWorkerService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.AppCommonService.dto.request.GrantAccessRequest;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.AppCommonService.event.WatermarkProcessEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.kafka.producer.WatermarkProcessProducer;
import com.system_share_documents.AppCommonService.rest.documentKey.DocumentKeyRest;
import com.system_share_documents.AppCommonService.rest.grantAccess.GrantAccessRest;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.AppCommonService.service.CryptoService;
import com.system_share_documents.AppCommonService.service.VaultTransitService;
import com.system_share_documents.WatermarkWorkerService.entity.WatermarkJob;
import com.system_share_documents.WatermarkWorkerService.enums.WorkerProcessStatus;
import com.system_share_documents.WatermarkWorkerService.exception.AppException;
import com.system_share_documents.WatermarkWorkerService.exception.errorcode.BusinessError;
import com.system_share_documents.WatermarkWorkerService.repository.WatermarkJobRepository;
import com.system_share_documents.WatermarkWorkerService.service.WaterMarkService;
import com.system_share_documents.WatermarkWorkerService.service.WatermarkProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_AES256;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT_VERSION;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.*;
import static com.system_share_documents.AppCommonService.utils.UserCacheUtils.getUserFullName;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.guessExtension;

@Service
public class WatermarkProcessorServiceImpl implements WatermarkProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkProcessorServiceImpl.class);

    private final MinioStorageRest minio;
    private final WaterMarkService wmService;
    private final CryptoService crypto;
    private final VaultTransitService vaultTransitService;
    private final WatermarkProcessProducer processProducer;
    private final WatermarkJobRepository jobRepo;
    private final DocumentKeyRest keyRest;
    private final GrantAccessRest grantAccessRest;
    private final AuditLogProducer auditLogProducer;

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF = 1000L;
    private static final int MAX_KEY_POOL = 8;

    public WatermarkProcessorServiceImpl(
            MinioStorageRest minioStorageRest,
            WaterMarkService waterMarkService,
            CryptoService cryptoService,
            VaultTransitService vaultTransitService,
            WatermarkProcessProducer watermarkProcessProducer,
            WatermarkJobRepository watermarkJobRepository,
            DocumentKeyRest documentKeyRest,
            GrantAccessRest grantAccessRest,
            AuditLogProducer auditLogProducer) {

        this.minio = minioStorageRest;
        this.wmService = waterMarkService;
        this.crypto = cryptoService;
        this.vaultTransitService = vaultTransitService;
        this.processProducer = watermarkProcessProducer;
        this.jobRepo = watermarkJobRepository;
        this.keyRest = documentKeyRest;
        this.grantAccessRest = grantAccessRest;
        this.auditLogProducer = auditLogProducer;
    }

    @Override
    public CompletableFuture<Void> processWatermark(WatermarkJobEvent event, String topic) {
        String requestId = event.getRequestId();
        log.info("[requestId={}] Consumed watermark for documentId={}, versionId={} -> Event: {}",
                requestId, event.getDocumentId(), event.getVersionId(), event);

        WatermarkJob job = initJob(event);

        Supplier<CompletableFuture<ProcessResult>> watermarkStageSupplier = () -> doWatermarkEncryptUpload(job, event);

        return retryAsync(watermarkStageSupplier, MAX_ATTEMPTS, BASE_BACKOFF, job)
                .thenCompose(result -> CompletableFuture.runAsync(() -> {
                    try {
                        log.info("[requestId={}] Watermark/encrypt/upload completed, saving keys for {} recipients",
                                requestId, event.getRecipients() != null ? event.getRecipients().size() : 0);

                        saveKeysOnce(event, result.wrappedCEKMaster, job);

                        log.info("[requestId={}] Creating grant access", requestId);
                        createGrantAccessForUser(job, event);

                        job.setStatus(WorkerProcessStatus.DONE);
                        job.setGeneratedObjectKey(result.objectKey);
                        job.setCompletedAt(Timestamp.from(Instant.now()));
                        jobRepo.save(job);

                        WatermarkProcessEvent processEvent = WatermarkProcessEvent.builder()
                                .requestId(event.getRequestId())
                                .documentId(event.getDocumentId())
                                .versionId(event.getVersionId())
                                .watermarkedKey(result.objectKey)
                                .checksum(result.checksum)
                                .createAt(Timestamp.from(Instant.now()))
                                .build();

                        processProducer.sendWatermarkProcess(topic, processEvent, event.getDocumentId());
                        log.info("[requestId={}] Watermark process completed successfully, objectKey={}",
                                requestId, result.objectKey);

                        sendAudit(event, "OK", null, job);

                    } catch (Exception ex) {
                        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                        log.error("[requestId={}] Post-process failed: {}", requestId, message, ex);
                        appendError(job, "Post-process failed: " + message);
                        job.setStatus(WorkerProcessStatus.FAILED);
                        job.setCompletedAt(Timestamp.from(Instant.now()));
                        jobRepo.save(job);
                        sendAudit(event, "FAIL", message, job);
                        throw new CompletionException(ex);
                    }
                }))
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    String err = cause == null ? ex.getMessage() : cause.getMessage();
                    log.error("[requestId={}] Watermark process permanently failed, error={}", requestId, err, cause);
                    try {
                        job.setStatus(WorkerProcessStatus.FAILED);
                        job.setCompletedAt(Timestamp.from(Instant.now()));
                        appendError(job, "Permanent failure: " + err);
                        jobRepo.save(job);
                        sendAudit(event, "FAIL", err, job);
                    } catch (Exception inner) {
                        log.error("[requestId={}] Error while marking job failed: {}", requestId, inner.getMessage(), inner);
                    }
                    return null;
                });
    }

    private WatermarkJob initJob(WatermarkJobEvent event) {
        String requestId = event.getRequestId();
        WatermarkJob existing = jobRepo.findTopByDocumentIdOrderByCreatedAtDesc(event.getDocumentId()).orElse(null);
        if (existing != null && existing.getStatus() == WorkerProcessStatus.DONE) {
            log.info("[requestId={}] Job already DONE, skipping", requestId);
            return existing;
        }

        Instant now = Instant.now();
        String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(now.atZone(java.time.ZoneId.systemDefault()));
        String watermarkText = String.format("%s | %s", getUserFullName(event.getOwnerId()), formattedTime);

        WatermarkJob job = WatermarkJob.builder()
                .documentId(event.getDocumentId())
                .watermarkText(watermarkText)
                .recipientUserId(event.getOwnerId())
                .status(WorkerProcessStatus.PROCESSING)
                .processingNode(getHostName())
                .createdAt(Timestamp.from(Instant.now()))
                .attempts(0)
                .errorMessage("")
                .build();

        log.info("[requestId={}] Initialized watermark job, watermarkText={}", requestId, watermarkText);
        return jobRepo.save(job);
    }

    private <T> CompletableFuture<T> retryAsync(Supplier<CompletableFuture<T>> supplier,
                                                int maxAttempts, long baseBackoff, WatermarkJob job) {
        return retryAsyncInternal(supplier, 1, maxAttempts, baseBackoff, job);
    }

    private <T> CompletableFuture<T> retryAsyncInternal(Supplier<CompletableFuture<T>> supplier,
                                                        int attempt, int maxAttempts, long baseBackoff, WatermarkJob job) {

        return supplier.get().handle((result, ex) -> {
            if (ex == null) {
                job.setAttempts(attempt);
                jobRepo.save(job);
                return CompletableFuture.completedFuture(result);
            }

            String errMsg = String.format("Attempt %d failed: %s", attempt, ex.getMessage());
            appendError(job, errMsg);
            job.setAttempts(attempt);
            jobRepo.save(job);

            if (attempt >= maxAttempts) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(ex);
                return failed;
            }

            long backoff = baseBackoff * (long) Math.pow(2, attempt - 1);
            log.warn("[requestId={}] Attempt {} failed, retry after {}ms, error={}",
                    job.getDocumentId(), attempt, backoff, ex.getMessage());

            CompletableFuture<T> delayed = new CompletableFuture<>();
            CompletableFuture.delayedExecutor(backoff, TimeUnit.MILLISECONDS)
                    .execute(() ->
                            retryAsyncInternal(supplier, attempt + 1, maxAttempts, baseBackoff, job)
                                    .whenComplete((r, ex2) -> {
                                        if (ex2 == null) delayed.complete(r);
                                        else delayed.completeExceptionally(ex2);
                                    })
                    );

            return delayed;
        }).thenCompose(f -> f);
    }

    private CompletableFuture<ProcessResult> doWatermarkEncryptUpload(WatermarkJob job, WatermarkJobEvent event) {
        String requestId = event.getRequestId();
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("[requestId={}] Starting watermark/encrypt/upload stage", requestId);

                byte[] original = minio.getObjectBytes(event.getUploadObjectKey());
                log.debug("[requestId={}] Downloaded original file, size: {} bytes", requestId, original.length);

                byte[] watermarked = original;
                if(event.getIsWatermark()) {
                    watermarked = wmService.addWatermark(original, job.getWatermarkText());
                    log.debug("[requestId={}] Applied watermark, watermarked size: {} bytes for document {}", requestId, watermarked.length, event.getDocumentId());
                } else {
                    log.debug("[requestId={}] Skip applied watermark for document {}", requestId, event.getDocumentId());
                }

                SecretKey cek = crypto.generateAesKey();
                byte[] encryptedFile = crypto.encryptFile(watermarked, cek.getEncoded());
                log.debug("[requestId={}] Encrypted file, encrypted size: {} bytes", requestId, encryptedFile.length);

                String checksum = crypto.calculateSha256(new ByteArrayInputStream(encryptedFile));
                log.debug("[requestId={}] Calculated checksum: {}", requestId, checksum);

                String ext = guessExtension(event.getContentType());
                String objKey = "final/%s/v1/%s_wm.%s".formatted(event.getDocumentId(), event.getDocumentId(), ext);
                minio.putObjectBytes(objKey, encryptedFile, event.getContentType());
                log.info("[requestId={}] Uploaded watermarked file to MinIO, objectKey={}", requestId, objKey);

                String base64Plaintext = Base64.getEncoder().encodeToString(cek.getEncoded());
                String wrappedCEKMaster = vaultTransitService.encrypt(base64Plaintext);
                log.debug("[requestId={}] Wrapped CEK using Vault Transit", requestId);

                return new ProcessResult(objKey, checksum, wrappedCEKMaster);

            } catch (Exception ex) {
                log.error("[requestId={}] Watermark/encrypt/upload failed: {}", requestId, ex.getMessage(), ex);
                throw new CompletionException(new AppException(BusinessError.FAILED_PROCESS_WATERMARK, ex.getMessage()));
            }
        });
    }

    private void saveKeysOnce(WatermarkJobEvent event, String wrappedCEKMaster, WatermarkJob job) {
        String requestId = event.getRequestId();
        List<String> recipients = event.getRecipients();
        if (recipients == null || recipients.isEmpty()) {
            log.warn("[requestId={}] No recipients found, skipping key save", requestId);
            return;
        }

        int poolSize = Math.min(Math.max(1, recipients.size()), MAX_KEY_POOL);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        byte[] wrappedCEKMasterBytes = wrappedCEKMaster.getBytes(StandardCharsets.UTF_8);
        try {
            List<CompletableFuture<Void>> futures = recipients.stream()
                    .map(recipient -> CompletableFuture.runAsync(() -> {
                        try {
                            CreateDocumentKeyRequest req = CreateDocumentKeyRequest.builder()
                                    .documentVersionId(UUID.fromString(event.getVersionId()))
                                    .recipientId(recipient)
                                    .wrappedByVault(wrappedCEKMasterBytes)
                                    .algorithm(OPENPGP_AES256)
                                    .build();

                            keyRest.createAndSaveKey(req);
                            log.debug("[requestId={}] Saved key for recipient: {}", requestId, recipient);
                        } catch (Exception e) {
                            String msg = "Failed save key for " + recipient + ": " + e.getMessage();
                            log.error("[requestId={}] {}", requestId, msg, e);
                            appendError(job, msg);
                        }
                    }, pool))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("[requestId={}] Saved keys for {} recipients", requestId, recipients.size());

        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void createGrantAccessForUser(WatermarkJob job, WatermarkJobEvent event) throws Exception {
        String requestId = event.getRequestId();
        try {
            if (event.getRecipients() == null || event.getRecipients().isEmpty()) {
                log.warn("[requestId={}] Recipients empty when creating grant access", requestId);
            }

            GrantAccessRequest request = GrantAccessRequest.builder()
                    .documentId(event.getDocumentId())
                    .recipients(event.getRecipients().stream()
                            .map(recipient -> {
                                boolean isOwner = recipient.equals(event.getOwnerId());
                                return GrantAccessRequest.AccessRecipientRequest.builder()
                                        .recipientUserId(recipient)
                                        .accessRole(isOwner ? "OWNER" : "VIEWER")
                                        .canDownload(false)
                                        .expirationDays(null)
                                        .build();
                            })
                            .toList())
                    .build();
            grantAccessRest.createGrantAccess(request);
            log.info("[requestId={}] Created grant access for {} recipients",
                    requestId, event.getRecipients() != null ? event.getRecipients().size() : 0);

        } catch (Exception ex) {
            String msg = "Failed create grant access: " + ex.getMessage();
            log.error("[requestId={}] {}", requestId, msg, ex);
            appendError(job, msg);
            throw ex;
        }
    }

    private void appendError(WatermarkJob job, String msg) {
        synchronized (job) {
            String old = job.getErrorMessage() == null ? "" : job.getErrorMessage();
            job.setErrorMessage(old + "[" + Instant.now() + "] " + msg + "\n");
            jobRepo.save(job);
        }
    }

    private void sendAudit(WatermarkJobEvent event, String status, String err, WatermarkJob job) {
        AuditLogEvent audit = AuditLogEvent.builder()
                .requestId(UUID.randomUUID().toString())
                .userId(event.getOwnerId())
                .action(String.valueOf(ActionLog.WATERMARK))
                .documentId(event.getDocumentId())
                .objectType(DOCUMENT_VERSION)
                .status(status)
                .errorReason(err)
                .ip(job != null ? job.getProcessingNode() : getHostName())
                .userAgent("worker-watermark")
                .request(String.valueOf(event))
                .build();

        auditLogProducer.sendAuditLog(audit, event.getDocumentId());
    }

    private static class ProcessResult {
        final String objectKey;
        final String checksum;
        final String wrappedCEKMaster;

        ProcessResult(String objectKey, String checksum, String wrappedCEKMaster) {
            this.objectKey = objectKey;
            this.checksum = checksum;
            this.wrappedCEKMaster = wrappedCEKMaster;
        }
    }
}
