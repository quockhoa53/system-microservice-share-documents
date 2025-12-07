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
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_AES256;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT_VERSION;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.*;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.guessExtension;

/**
 * Clean, safe and refactored implementation.
 *
 * Key ideas:
 *  - Retry ONLY watermark + encrypt + upload stage (idempotent upload path).
 *  - Save CEK per recipient exactly once after upload success (no retry).
 *  - Record attempts and append detailed errors into job record.
 *  - Executor pool for key saves sized to recipient count (capped).
 */
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
        // Initialize job (idempotency guard)
        WatermarkJob job = initJob(event);

        Supplier<CompletableFuture<ProcessResult>> watermarkStageSupplier = () -> doWatermarkEncryptUpload(job, event);

        // Retry ONLY watermark/encrypt/upload stage
        return retryAsync(watermarkStageSupplier, MAX_ATTEMPTS, BASE_BACKOFF, job)
                .thenCompose(result -> CompletableFuture.runAsync(() -> {
                    // Post-processing that must run exactly once (no retry at orchestration level)
                    try {
                        // 1) Save CEK for recipients (parallel but one-time)
                        saveKeysOnce(event, result.wrappedCEKMaster, job);

                        // 2) Create grant access
                        createGrantAccessForUser(job, event);

                        // 3) Finalize job state & emit event
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

                        sendAudit(event, "OK", null, job);

                    } catch (Exception ex) {
                        // Post-process failure -> record and mark FAILED (do not retry the upload stage here)
                        String message = ex.getMessage() == null ? ex.toString() : ex.getMessage();
                        appendError(job, "Post-process failed: " + message);
                        job.setStatus(WorkerProcessStatus.FAILED);
                        job.setCompletedAt(Timestamp.from(Instant.now()));
                        jobRepo.save(job);
                        sendAudit(event, "FAIL", message, job);
                        throw new CompletionException(ex);
                    }
                }))
                .exceptionally(ex -> {
                    // Any exception from retry or post-process will be handled/logged here.
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    String err = cause == null ? ex.getMessage() : cause.getMessage();
                    log.error("Watermark process permanently failed for documentId={} requestId={} error={}",
                            event.getDocumentId(), event.getRequestId(), err);
                    // ensure job marked failed (if not already)
                    try {
                        job.setStatus(WorkerProcessStatus.FAILED);
                        job.setCompletedAt(Timestamp.from(Instant.now()));
                        appendError(job, "Permanent failure: " + err);
                        jobRepo.save(job);
                        sendAudit(event, "FAIL", err, job);
                    } catch (Exception inner) {
                        log.error("Error while marking job failed: {}", inner.getMessage(), inner);
                    }
                    return null;
                });
    }

    /**
     * Initialize job or return existing DONE job (idempotent).
     */
    private WatermarkJob initJob(WatermarkJobEvent event) {
        WatermarkJob existing = jobRepo.findTopByDocumentIdOrderByCreatedAtDesc(event.getDocumentId()).orElse(null);
        if (existing != null && existing.getStatus() == WorkerProcessStatus.DONE) {
            log.info("Job already DONE → skip. documentId={}", event.getDocumentId());
            return existing;
        }

        WatermarkJob job = WatermarkJob.builder()
                .documentId(event.getDocumentId())
                .watermarkText(event.getOwnerId() + " | " + Instant.now())
                .recipientUserId(event.getOwnerId())
                .status(WorkerProcessStatus.PROCESSING)
                .processingNode(getHostName())
                .createdAt(Timestamp.from(Instant.now()))
                .attempts(0)
                .errorMessage("")
                .build();

        return jobRepo.save(job);
    }

    /**
     * Retry helper that records attempts and appends error details into job record.
     */
    private <T> CompletableFuture<T> retryAsync(Supplier<CompletableFuture<T>> supplier,
                                                int maxAttempts, long baseBackoff, WatermarkJob job) {
        return retryAsyncInternal(supplier, 1, maxAttempts, baseBackoff, job);
    }

    private <T> CompletableFuture<T> retryAsyncInternal(Supplier<CompletableFuture<T>> supplier,
                                                        int attempt, int maxAttempts, long baseBackoff, WatermarkJob job) {

        return supplier.get().handle((result, ex) -> {
            if (ex == null) {
                // success: persist attempts (useful metrics)
                job.setAttempts(attempt);
                jobRepo.save(job);
                return CompletableFuture.completedFuture(result);
            }

            // record attempt failure with detail
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
            log.warn("Attempt {} failed — retry after {} ms (docId={}, attemptErr={})",
                    attempt, backoff, job.getDocumentId(), ex.getMessage());

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

    /**
     * The stage that we treat as retryable: watermark -> encrypt -> upload -> wrap CEK.
     * Returns ProcessResult used by post-processing.
     */
    private CompletableFuture<ProcessResult> doWatermarkEncryptUpload(WatermarkJob job, WatermarkJobEvent event) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Start watermark/encrypt/upload stage for documentId={}", job.getDocumentId());

                // 1. Get original bytes
                byte[] original = minio.getObjectBytes(event.getUploadObjectKey());

                // 2. Apply watermark
                byte[] watermarked = wmService.addWatermark(original, job.getWatermarkText());

                // 3. Generate CEK and encrypt file bytes
                SecretKey cek = crypto.generateAesKey();
                byte[] encryptedFile = crypto.encryptFile(watermarked, cek.getEncoded());

                // 4. Compute checksum
                String checksum = crypto.calculateSha256(new ByteArrayInputStream(encryptedFile));

                // 5. Upload encrypted file (idempotent path)
                String ext = guessExtension(event.getContentType());
                String objKey = "final/%s/v1/%s_wm.%s".formatted(event.getDocumentId(), event.getDocumentId(), ext);
                minio.putObjectBytes(objKey, encryptedFile, event.getContentType());

                // 6. Wrap CEK using Vault Transit
                String base64Plaintext = Base64.getEncoder().encodeToString(cek.getEncoded());
                String wrappedCEKMaster = vaultTransitService.encrypt(base64Plaintext);

                log.info("Watermark/encrypt/upload done for documentId={}, objectKey={}", job.getDocumentId(), objKey);
                return new ProcessResult(objKey, checksum, wrappedCEKMaster);

            } catch (Exception ex) {
                log.error("doWatermarkEncryptUpload failed for documentId={} : {}", job.getDocumentId(), ex.getMessage());
                throw new CompletionException(new AppException(BusinessError.FAILED_PROCESS_WATERMARK, ex.getMessage()));
            }
        });
    }

    /**
     * Save CEK per recipient exactly once. Executor pool sized to recipients (capped).
     * Duplicate key exceptions are handled gracefully (logged + appended to job error) without failing whole post-process.
     */
    private void saveKeysOnce(WatermarkJobEvent event, String wrappedCEKMaster, WatermarkJob job) {
        List<String> recipients = event.getRecipients();
        if (recipients == null || recipients.isEmpty()) {
            log.warn("No recipients found for documentId={}. skip saving keys.", event.getDocumentId());
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
                        } catch (Exception e) {
                            // record error but continue other recipients
                            String msg = "Failed save key for " + recipient + ": " + e.getMessage();
                            appendError(job, msg);
                            log.error(msg, e);
                        }
                    }, pool))
                    .toList();

            // Wait for all recipient key tasks to finish
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

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

    /**
     * Create grant access for recipients (single call). If it fails, throw to let post-process mark job failed.
     */
    private void createGrantAccessForUser(WatermarkJob job, WatermarkJobEvent event) throws Exception {
        try {
            if (event.getRecipients() == null || event.getRecipients().isEmpty()) {
                log.warn("Recipients empty when creating grant access for version {} of document {}", event.getVersionId(), event.getDocumentId());
            }

            GrantAccessRequest request = GrantAccessRequest.builder()
                    .documentId(event.getDocumentId())
                    .recipients(event.getRecipients().stream()
                            .map(recipient -> GrantAccessRequest.AccessRecipientRequest.builder()
                                    .recipientUserId(recipient)
                                    .accessRole("VIEWER")
                                    .canDownload(false)
                                    .build())
                            .toList())
                    .build();

            grantAccessRest.createGrantAccess(request);

        } catch (Exception ex) {
            String msg = "Failed create grant access for document " + event.getDocumentId() + ": " + ex.getMessage();
            appendError(job, msg);
            log.error(msg, ex);
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

    /**
     * Container for result from watermark/encrypt/upload stage.
     */
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
