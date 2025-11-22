package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.kafka.producer.WatermarkJobProducer;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.DocumentService.dto.request.CompleteUploadRequest;
import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.CompleteUploadResponse;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;
import com.system_share_documents.DocumentService.dto.response.UploadUrlsResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.service.*;
import com.system_share_documents.DocumentService.utils.HexUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_ED25519;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Service
public class UploadDocumentServiceImpl implements UploadDocumentService {

    @Autowired
    private OpenPgpService openPgpService;

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

    @Autowired
    private WatermarkJobProducer watermarkJobProducer;

    private final int presignExpiryMinutes = 15;

    /**
     * Khởi tạo quy trình upload tài liệu:
     * 1. Tạo bản ghi Document và DocumentVersion
     * 2. Tạo đường dẫn Upload file lên Minio, có hiệu lực trong 15p
     * @param request  Thông tin file upload từ client
     * @param ownerId  ID user upload (lấy từ Authentication)
     * @return InitUploadResponse chứa thông tin document, version và URL upload
     */
    @Override
    @Transactional
    public InitUploadResponse initUpload(InitUploadRequest request, String ownerId, HttpServletRequest httpRequest) {
        String status = "OK";
        String errorReason = null;
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
            documentRepository.save(doc);

            version =  documentVersionService.createDocumentVersion(doc, objectKey, request.getSizeBytes());

            urls = new UploadUrlsResponse(objectKey, minioStorageRest.generatePreSignedPutUrl(objectKey, presignExpiryMinutes), null);

            return new InitUploadResponse(doc.getId(), version.getVersionNumber(), urls, true);
        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_INIT_UPLOAD, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(ownerId)
                        .action(String.valueOf(ActionLog.INIT_UPLOAD))
                        .documentId(doc.getId().toString())
                        .objectType(DOCUMENT)
                        .status(status)
                        .errorReason(errorReason)
                        .ip(getClientIp(httpRequest))
                        .userAgent(getUserAgent(httpRequest))
                        .metadata(null)
                        .request(String.valueOf(request))
                        .build();
                auditLogProducer.sendAuditLog(logEvent, doc.getId().toString());
            }
        }
    }

    /**
     * Hoàn tất upload tài liệu và khởi tạo watermarking:
     *
     * 1. Lấy Document và DocumentVersion theo request.
     * 2. Lấy public key của người ký (người chia sẻ file)
     * 3. Lấy file từ Minio và kiểm tra signature
     * 4. Kiểm tra checksum SHA-256
     * 5. Cập nhật trạng thái version sang WATERMARKING và lưu document.
     * 6. Tạo WatermarkJobEvent và gửi tới Kafka để thực hiện watermarking.
     *
     * @param request Thông tin upload từ client
     * @param httpRequest Thông tin HTTP request (IP, UserAgent)
     * @return CompleteUploadResponse với thông tin document, version, uploadObjectKey, checksum, size, status
     * @throws Exception nếu có lỗi trong quá trình xác thực hoặc lưu trữ
     */
    @Override
    @Transactional
    public CompleteUploadResponse completeUpload(CompleteUploadRequest request, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        Document doc = null;
        DocumentVersion version = null;
        try {
            doc = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            version = doc.getVersions().stream()
                    .filter(v -> v.getVersionNumber() == request.getVersionNumber())
                    .findFirst()
                    .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

            String singerPublicKey = userKeyRest.getUserPublicPrimaryKeyForUser(request.getSignerUserId().toString(), OPENPGP_ED25519);
            if(singerPublicKey == null) {
                throw new AppException(NotExistError.SINGER_PUBLIC_KEY_EMPTY);
            }

            byte[] detachedSignature = Base64.getDecoder().decode(request.getSignature());
            byte[] fileBytes = minioStorageRest.getObjectBytes(request.getUploadObjectKey());
            if(fileBytes == null){
                throw new AppException(NotExistError.FILE_BYTE_EMPTY);
            }
            boolean validSignature = openPgpService.verifyDetachedSignature(fileBytes, detachedSignature, singerPublicKey);
            if (!validSignature) {
                throw new AppException(ValidationError.SIGNATURE_INVALID);
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] computedHash = md.digest(fileBytes);
            String computedHex = HexUtils.encode(computedHash);
            String expectedHex = request.getChecksum();
            if (expectedHex.startsWith("sha256:")) {
                expectedHex = expectedHex.substring(7);
            }
            if (!computedHex.equalsIgnoreCase(expectedHex)) {
                throw new AppException(ValidationError.CHECKSUM_MISMATCH);
            }

            version.setStatus(VersionStatus.WATERMARKING);
            version.setUpdatedAt(Timestamp.from(Instant.now()));
            documentRepository.save(doc);

            WatermarkJobEvent jobEvent = WatermarkJobEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .documentId(String.valueOf(doc.getId()))
                    .versionId(String.valueOf(version.getId()))
                    .uploadObjectKey(request.getUploadObjectKey())
                    .contentType(doc.getContentType())
                    .ownerId(doc.getOwnerId())
                    .recipients(request.getRecipients())
                    .checksum(request.getChecksum())
                    .build();
            watermarkJobProducer.sendWatermarkJob(jobEvent, doc.getId().toString());

            return new CompleteUploadResponse(
                    doc.getId(),
                    version.getId(),
                    request.getUploadObjectKey(),
                    request.getChecksum(),
                    version.getSizeBytes(),
                    version.getStatus().toString()
            );
        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_COMPLETED_UPLOAD, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(String.valueOf(request.getSignerUserId()))
                        .action(String.valueOf(ActionLog.COMPLETE_UPLOAD))
                        .documentId(doc.getId().toString())
                        .objectType(DOCUMENT)
                        .status(status)
                        .errorReason(errorReason)
                        .ip(getClientIp(httpRequest))
                        .userAgent(getUserAgent(httpRequest))
                        .metadata(null)
                        .request(String.valueOf(request))
                        .build();

                auditLogProducer.sendAuditLog(logEvent, doc.getId().toString());
            }
        }
    }
}
