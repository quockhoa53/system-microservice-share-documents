package com.system_share_documents.WatermarkWorkerService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.AppCommonService.event.WatermarkProcessEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.kafka.producer.WatermarkProcessProducer;
import com.system_share_documents.AppCommonService.rest.documentKey.DocumentKeyRest;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.AppCommonService.service.CryptoService;
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_AES256;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT_VERSION;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.WATERMARK_DOCUMENT;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.*;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.guessExtension;

@Service
public class WatermarkProcessorServiceImpl implements WatermarkProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkProcessorServiceImpl.class);

    private final MinioStorageRest minio;
    private final WaterMarkService wmService;
    private final CryptoService crypto;
    private final WatermarkProcessProducer processProducer;
    private final WatermarkJobRepository jobRepo;
    private final DocumentKeyRest keyRest;
    private final AuditLogProducer auditLogProducer;

    private final ExecutorService keyExecutor = Executors.newFixedThreadPool(8);

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF = 1000L;

    public WatermarkProcessorServiceImpl(
            MinioStorageRest minioStorageRest,
            WaterMarkService waterMarkService,
            CryptoService cryptoService,
            WatermarkProcessProducer watermarkProcessProducer,
            WatermarkJobRepository watermarkJobRepository,
            DocumentKeyRest documentKeyRest,
            AuditLogProducer auditLogProducer) {

        this.minio = minioStorageRest;
        this.wmService = waterMarkService;
        this.crypto = cryptoService;
        this.processProducer = watermarkProcessProducer;
        this.jobRepo = watermarkJobRepository;
        this.keyRest = documentKeyRest;
        this.auditLogProducer = auditLogProducer;
    }

    /**
     * Entry chính – luôn trả async future
     */
    @Override
    public CompletableFuture<Void> processWatermark(WatermarkJobEvent event, String topic) {

        return CompletableFuture.supplyAsync(() -> initJob(event))
                .thenCompose(job -> retryAsync(() -> doProcess(job, event, topic), MAX_ATTEMPTS, BASE_BACKOFF))
                .exceptionally(ex -> {
                    log.error("Watermark permanently failed requestId={}", event.getRequestId(), ex);
                    updateFailedJob(event.getRequestId(), ex.getMessage());
                    return null;
                });
    }

    private WatermarkJob initJob(WatermarkJobEvent event) {
        // Check idempotency
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
     * Retry async với exponential backoff
     */
    private <T> CompletableFuture<T> retryAsync(Supplier<CompletableFuture<T>> supplier, int maxAttempts, long baseBackoff) {
        return retryAsyncInternal(supplier, 1, maxAttempts, baseBackoff);
    }

    private <T> CompletableFuture<T> retryAsyncInternal(Supplier<CompletableFuture<T>> supplier,
                                                        int attempt, int maxAttempts, long baseBackoff) {

        return supplier.get().handle((result, ex) -> {

            if (ex == null) return CompletableFuture.completedFuture(result);

            if (attempt >= maxAttempts) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(ex);
                return failed;
            }

            long backoff = baseBackoff * (long) Math.pow(2, attempt - 1);
            log.warn("Attempt {} failed — retry after {} ms", attempt, backoff);

            CompletableFuture<T> delayed = new CompletableFuture<>();
            CompletableFuture.delayedExecutor(backoff, TimeUnit.MILLISECONDS)
                    .execute(() -> retryAsyncInternal(supplier, attempt + 1, maxAttempts, baseBackoff)
                            .whenComplete((r, ex2) -> {
                                if (ex2 == null) delayed.complete(r);
                                else delayed.completeExceptionally(ex2);
                            })
                    );

            return delayed;
        }).thenCompose(f -> f);
    }

    /**
     * Toàn bộ logic watermark + upload + encrypt + save keys
     */
    private CompletableFuture<Void> doProcess(WatermarkJob job,
                                              WatermarkJobEvent event,
                                              String topic) {

        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Processing watermark v2 documentId={}", job.getDocumentId());

                // 1. Get original file
                byte[] original = minio.getObjectBytes(event.getUploadObjectKey());

                // 2. Watermark
                byte[] watermarked = wmService.addWatermark(original, job.getWatermarkText());

                // 3. Encrypt CEK
                SecretKey cek = crypto.generateAesKey();
                byte[] encrypted = crypto.encryptFile(watermarked, cek.getEncoded());

                // 4. Compute checksum
                String checksum = crypto.calculateSha256(new ByteArrayInputStream(encrypted));

                // 5. Upload encrypted
                String ext = guessExtension(event.getContentType());
                String objKey = "final/%s/v1/%s_wm.%s".formatted(event.getDocumentId(), event.getDocumentId(), ext);
                minio.putObjectBytes(objKey, encrypted, event.getContentType());

                // 6. Save keys per-recipient (parallel)
                saveKeysAsync(event, cek, job);

                // 7. Update job + send event
                job.setStatus(WorkerProcessStatus.DONE);
                job.setGeneratedObjectKey(objKey);
                job.setCompletedAt(Timestamp.from(Instant.now()));
                jobRepo.save(job);

                WatermarkProcessEvent processEvent = WatermarkProcessEvent.builder()
                        .requestId(event.getRequestId())
                        .documentId(event.getDocumentId())
                        .versionId(event.getVersionId())
                        .watermarkedKey(objKey)
                        .checksum(checksum)
                        .createAt(Timestamp.from(Instant.now()))
                        .build();

                processProducer.sendWatermarkProcess(topic, processEvent, event.getDocumentId());

                sendAudit(event, "OK", null, job);

            } catch (Exception ex) {
                sendAudit(event, "FAIL", ex.getMessage(), job);
                throw new AppException(BusinessError.FAILED_PROCESS_WATERMARK, ex.getMessage());
            }
        });
    }

    private void saveKeysAsync(WatermarkJobEvent event, SecretKey cek, WatermarkJob job) {

        List<CompletableFuture<Void>> futures =
                event.getRecipients().stream().map(recipient ->
                        CompletableFuture.runAsync(() -> {
                            try {
                                CreateDocumentKeyRequest req = CreateDocumentKeyRequest.builder()
                                        .documentVersionId(UUID.fromString(event.getVersionId()))
                                        .recipientId(recipient)
                                        .rawCek(cek.getEncoded())
                                        .algorithm(OPENPGP_AES256)
                                        .build();

                                keyRest.createAndSaveKey(req);

                            } catch (Exception ex) {
                                appendError(job, "Failed save key for %s: %s".formatted(recipient, ex.getMessage()));
                                throw new RuntimeException(ex);
                            }

                        }, keyExecutor)
                ).toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void appendError(WatermarkJob job, String msg) {
        synchronized (job) {
            String old = job.getErrorMessage() == null ? "" : job.getErrorMessage();
            job.setErrorMessage(old + "[" + Instant.now() + "] " + msg + "\n");
            jobRepo.save(job);
        }
    }

    private void updateFailedJob(String requestId, String err) {
        WatermarkJob job = jobRepo.findById(requestId).orElse(null);
        if (job == null) return;

        job.setStatus(WorkerProcessStatus.FAILED);
        job.setCompletedAt(Timestamp.from(Instant.now()));
        appendError(job, err);
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
}

