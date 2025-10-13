package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.DocumentService.dto.request.CompleteUploadRequest;
import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.CompleteUploadResponse;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;
import com.system_share_documents.DocumentService.dto.response.UploadUrlsResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.SecretKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_ED25519;
import static com.system_share_documents.AppCommonService.constant.NameConstant.DOCUMENT_SERVICE;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Service
public class UploadDocumentServiceImpl implements UploadDocumentService {

    private final ExecutorService cekExecutor = Executors.newFixedThreadPool(4);

    private final ExecutorService uploadExecutor = Executors.newFixedThreadPool(4);

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private DocumentKeyService documentKeyService;

    @Autowired
    private DocumentVersionService documentVersionService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserKeyRest userKeyRest;

    @Autowired
    private MinioStorageRest minioStorageRest;

    @Autowired
    private AuditLogProducer auditLogProducer;

    private final int presignExpiryMinutes = 15;

    /**
     * Khởi tạo quy trình upload tài liệu:
     * 1. Tạo bản ghi Document và DocumentVersion
     * 2. Sinh CEK (Content Encryption Key)
     * 3. Tạo pre-signed URL cho client upload file
     * 4. Persist Document + cascade DocumentVersion và DocumentKey
     * 5. Trả về response
     * Lưu ý:
     *   - Đây là quy trình "khởi tạo" upload, file thực tế chưa có trong bucket.
     *   - Client bắt buộc phải PUT file lên pre-signed URL để hoàn tất upload.
     *   - Nếu pre-signed URL hết hạn, cần sinh lại URL mới.
     *
     * @param request  Thông tin file upload từ client
     * @param ownerId  ID user upload (lấy từ Authentication)
     * @return InitUploadResponse chứa thông tin document, version và URL upload
     */
    @Override
    @Transactional
    public InitUploadResponse initUpload(InitUploadRequest request, String ownerId, HttpServletRequest httpRequest) {
        String status = "OK";
        Document doc = null;
        DocumentVersion version = null;
        UploadUrlsResponse urls = null;

        try {
            String objectKey = String.format("staging/%s/v%d/%s", UUID.randomUUID(), 1, UUID.randomUUID());

            doc = Document.builder()
                    .ownerId(ownerId)
                    .originalFilename(request.getOriginalFilename())
                    .contentType(request.getContentType())
                    .sizeBytes(request.getSizeBytes())
                    .storageClass(request.getStorageClass() == null ? "standard" : request.getStorageClass())
                    .metadata(request.getMetadata())
                    .createdAt(Timestamp.from(Instant.now()))
                    .updatedAt(Timestamp.from(Instant.now()))
                    .build();
            documentRepository.saveAndFlush(doc);

            version = documentVersionService.createDocumentVersion(doc, objectKey, request.getSizeBytes());

            urls = new UploadUrlsResponse(objectKey, minioStorageRest.generatePreSignedPutUrl(objectKey, presignExpiryMinutes), null);

            DocumentVersion finalVersion = version;
            SecretKey cek = cryptoService.generateAesKey();
            byte[] cekBytes = cek.getEncoded();
            List<String> recipients = request.getRecipients();
            for (String recipient : recipients) {
                CompletableFuture.runAsync(() -> {
                    try {
                        documentKeyService.createAndSaveKey(recipient, finalVersion, cekBytes);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, cekExecutor);
            }

        } catch (Exception e) {
            status = "FAIL";
            e.printStackTrace();
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .userId(ownerId)
                        .action(String.valueOf(ActionLog.INIT_UPLOAD))
                        .documentId(doc.getId().toString())
                        .objectType("document")
                        .status(status)
                        .ip(getClientIp(httpRequest))
                        .userAgent(getUserAgent(httpRequest))
                        .metadata(null)
                        .request(String.valueOf(request))
                        .build();

                auditLogProducer.sendAuditLog(logEvent, DOCUMENT_SERVICE);
            }
        }

        if ("FAIL".equals(status)) {
            throw new AppException(BusinessError.FAILED_INIT_UPLOAD);
        }

        return new InitUploadResponse(doc.getId(), version.getVersionNumber(), urls, true);
    }

    @Override
    @Transactional
    public CompleteUploadResponse completeUpload(CompleteUploadRequest request, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        Document doc = null;
        try {
            doc = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            DocumentVersion version = doc.getVersions().stream()
                    .filter(v -> v.getVersionNumber() == request.getVersionNumber())
                    .findFirst()
                    .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

            String singerPublicKey = userKeyRest.getUserPublicPrimaryKeyForUser(request.getSignerUserId().toString(), OPENPGP_ED25519);
            byte[] detachedSignature = Base64.getDecoder().decode(request.getSignature());
            byte[] fileBytes = minioStorageRest.getObjectBytes(request.getUploadObjectKey());
            boolean validSignature = openPgpService.verifyDetachedSignature(fileBytes, detachedSignature, singerPublicKey);
            if (!validSignature) {
                throw new AppException(ValidationError.SIGNATURE_INVALID);
            }


        } catch(Exception e){
            status = "FAIL";
        } finally {

        }

    }
}
