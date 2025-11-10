package com.system_share_documents.WatermarkWorkerService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.AppCommonService.event.WatermarkProcessEvent;
import com.system_share_documents.AppCommonService.kafka.producer.WatermarkProcessProducer;
import com.system_share_documents.AppCommonService.rest.documentKey.DocumentKeyRest;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.AppCommonService.service.CryptoService;
import com.system_share_documents.WatermarkWorkerService.entity.WatermarkJob;
import com.system_share_documents.WatermarkWorkerService.enums.WorkerProcessStatus;
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

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_AES256;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getHostName;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.guessExtension;

@Service
public class WatermarkProcessorServiceImpl implements WatermarkProcessorService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkProcessorServiceImpl.class);
    private final ExecutorService cekExecutor = Executors.newFixedThreadPool(4);

    private final MinioStorageRest minioStorageRest;
    private final WaterMarkService waterMarkService;
    private final CryptoService cryptoService;
    private final WatermarkProcessProducer watermarkProcessProducer;
    private final WatermarkJobRepository watermarkJobRepository;
    private final DocumentKeyRest documentKeyRest;

    public WatermarkProcessorServiceImpl(
            MinioStorageRest minioStorageRest,
            WaterMarkService waterMarkService,
            CryptoService cryptoService,
            WatermarkProcessProducer watermarkProcessProducer,
            WatermarkJobRepository watermarkJobRepository,
            DocumentKeyRest documentKeyRest
    ) {
        this.minioStorageRest = minioStorageRest;
        this.waterMarkService = waterMarkService;
        this.cryptoService = cryptoService;
        this.watermarkProcessProducer = watermarkProcessProducer;
        this.watermarkJobRepository = watermarkJobRepository;
        this.documentKeyRest = documentKeyRest;
    }

    @Override
    public void processWatermark(WatermarkJobEvent event, String topic) {
        int maxAttempts = 3;
        int attempt = 0;
        long backoffMillis = 1000L;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                log.info("Processing watermark (attempt {}/{}) [requestId={}, documentId={}]",
                        attempt, maxAttempts, event.getRequestId(), event.getDocumentId());

                doProcess(event, topic);
                log.info("Watermark job completed successfully after {} attempt(s).", attempt);
                return;

            } catch (Exception e) {
                log.error("Attempt {} failed for documentId={}, error={}", attempt, event.getDocumentId(), e.getMessage(), e);

                if (attempt < maxAttempts) {
                    try {
                        long sleep = backoffMillis * (long) Math.pow(2, attempt - 1);
                        log.warn("Retrying watermark job after {} ms (attempt {} of {})", sleep, attempt + 1, maxAttempts);
                        Thread.sleep(sleep);
                    } catch (InterruptedException ignored) {}
                } else {
                    handlePermanentFailure(event, e);
                    throw new RuntimeException("Watermark job permanently failed after retries", e);
                }
            }
        }
    }

    /** Logic chính (một lần xử lý) */
    private void doProcess(WatermarkJobEvent event, String topic) throws Exception {
        WatermarkJob job = WatermarkJob.builder()
                .documentId(event.getDocumentId())
                .recipientUserId(event.getOwnerId())
                .watermarkText(event.getOwnerId() + " | " + Instant.now())
                .status(WorkerProcessStatus.PROCESSING)
                .processingNode(getHostName())
                .createdAt(Timestamp.from(Instant.now()))
                .build();
        watermarkJobRepository.save(job);

        // 1️⃣ Lấy file gốc từ MinIO
        byte[] original = minioStorageRest.getObjectBytes(event.getUploadObjectKey());

        // 2️⃣ Thêm watermark
        byte[] watermarked = waterMarkService.addWatermark(original, job.getWatermarkText());

        // 3️⃣ Lựa chọn extension
        String extension = guessExtension(event.getContentType());
        String key = String.format("final/%s/v1/%s_watermarked.%s",
                event.getDocumentId(), event.getDocumentId(), extension);

        // 4️⃣ Mã hóa file trước khi upload
        SecretKey cek = cryptoService.generateAesKey();
        byte[] encrypted = cryptoService.encryptFile(watermarked, cek.getEncoded());

        // 5️⃣ Tính checksum SHA-256
        String checksum = cryptoService.calculateSha256(new ByteArrayInputStream(encrypted));

        // 6️⃣ Upload file mã hóa lên MinIO
        minioStorageRest.putObjectBytes(key, encrypted, event.getContentType());

        // 7️⃣ Lưu CEK cho từng người nhận song song
        List<CompletableFuture<Void>> tasks = event.getRecipients().stream().map(recipient ->
                CompletableFuture.runAsync(() -> {
                    try {
                        CreateDocumentKeyRequest req = CreateDocumentKeyRequest.builder()
                                .documentVersionId(UUID.fromString(event.getVersionId()))
                                .recipientId(recipient)
                                .rawCek(cek.getEncoded())
                                .algorithm(OPENPGP_AES256)
                                .build();
                        documentKeyRest.createAndSaveKey(req);
                        log.debug("Key saved for recipient {}", recipient);
                    } catch (Exception ex) {
                        throw new RuntimeException("Failed to save key for recipient " + recipient, ex);
                    }
                }, cekExecutor)
        ).toList();

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();

        // 8️⃣ Cập nhật job + gửi event
        job.setStatus(WorkerProcessStatus.DONE);
        job.setGeneratedObjectKey(key);
        job.setCompletedAt(Timestamp.from(Instant.now()));
        watermarkJobRepository.save(job);

        WatermarkProcessEvent processEvent = WatermarkProcessEvent.builder()
                .requestId(event.getRequestId())
                .documentId(event.getDocumentId())
                .versionId(event.getVersionId())
                .watermarkedKey(key)
                .checksum(checksum)
                .createAt(Timestamp.from(Instant.now()))
                .build();
        watermarkProcessProducer.sendWatermarkProcess(topic, processEvent, event.getDocumentId());
    }

    /** Ghi nhận thất bại vĩnh viễn */
    private void handlePermanentFailure(WatermarkJobEvent event, Exception e) {
        log.error("Permanent failure for documentId={}, error={}", event.getDocumentId(), e.getMessage());
        WatermarkJob job = WatermarkJob.builder()
                .documentId(event.getDocumentId())
                .status(WorkerProcessStatus.FAILED)
                .errorMessage(e.getMessage())
                .attempts(3)
                .completedAt(Timestamp.from(Instant.now()))
                .processingNode(getHostName())
                .build();
        watermarkJobRepository.save(job);
    }
}
