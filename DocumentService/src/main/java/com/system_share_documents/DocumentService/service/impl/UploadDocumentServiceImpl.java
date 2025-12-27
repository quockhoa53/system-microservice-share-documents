package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.event.MalwareScanJobEvent;
import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.kafka.producer.MalwareScanJobProducer;
import com.system_share_documents.AppCommonService.kafka.producer.WatermarkJobProducer;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.DocumentService.dto.request.CompleteUploadRequest;
import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.request.ReinitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.CompleteUploadResponse;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;
import com.system_share_documents.DocumentService.dto.response.UploadUrlsResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.entity.Signature;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.AuthError;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.repository.SignatureRepository;
import com.system_share_documents.DocumentService.service.*;
import com.system_share_documents.DocumentService.utils.HexUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_ED25519;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT;
import static com.system_share_documents.AppCommonService.utils.AuthenticationUtils.getUsername;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;
import static com.system_share_documents.AppCommonService.utils.ProcessJsonUtils.convertJson;

@Service
public class UploadDocumentServiceImpl implements UploadDocumentService {

    private static final Logger log = LoggerFactory.getLogger(UploadDocumentServiceImpl.class);

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private DocumentVersionService documentVersionService;

    @Autowired
    private FileValidationService fileValidationService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private SignatureRepository signatureRepository;

    @Autowired
    private UserKeyRest userKeyRest;

    @Autowired
    private MinioStorageRest minioStorageRest;

    @Autowired
    private AuditLogProducer auditLogProducer;

    @Autowired
    private WatermarkJobProducer watermarkJobProducer;

    @Autowired
    private MalwareScanJobProducer malwareScanJobProducer;

    private final int presignExpiryMinutes = 30;

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
    public InitUploadResponse initUpload(InitUploadRequest request, String ownerId, HttpServletRequest httpRequest) throws Exception{
        String status = "OK";
        String errorReason = null;
        Document doc = null;
        DocumentVersion version = null;
        UploadUrlsResponse urls = null;
        try {
            if (request.getDocumentId() != null) {
                doc = documentRepository.findById(UUID.fromString(request.getDocumentId()))
                        .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

                boolean changed = false;

                if (request.getOriginalFilename() != null && !request.getOriginalFilename().equals(doc.getOriginalFilename())) {
                    doc.setOriginalFilename(request.getOriginalFilename());
                    changed = true;
                }

                if (request.getContentType() != null && !request.getContentType().equals(doc.getContentType())) {
                    doc.setContentType(request.getContentType());
                    changed = true;
                }

                if (request.getContentType() != null && !request.getContentType().equals(doc.getContentType())) {
                    if (!fileValidationService.isSupportedFileType(request.getContentType())) {
                        throw new AppException(ValidationError.FILE_TYPE_NOT_SUPPORTED);
                    }
                    doc.setContentType(request.getContentType());
                    changed = true;
                }

                if (request.getMetadata() != null) {
                    doc.setMetadata(request.getMetadata());
                    changed = true;
                }

                if (changed) {
                    doc.setUpdatedAt(Timestamp.from(Instant.now()));
                    documentRepository.save(doc);
                }
            } else {
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
            }

            long versionCount = documentVersionRepository.findLatestVersionNumber(doc.getId());
            int newVersionNumber = (int) versionCount + 1;

            String objectKey = String.format("staging/%s/v%d/%s", UUID.randomUUID(), newVersionNumber, UUID.randomUUID());

            documentVersionService.createDocumentVersion(doc, newVersionNumber, objectKey, request.getSizeBytes());

            urls = new UploadUrlsResponse(objectKey, minioStorageRest.generatePreSignedPutUrl(objectKey, presignExpiryMinutes), null);

            return new InitUploadResponse(doc.getId(), newVersionNumber, urls, true);

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
                        .request(convertJson(request))
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

            doc.setChecksum(request.getChecksum());
            documentRepository.save(doc);

            String singerPublicKey = userKeyRest.getUserPublicPrimaryKeyForUser(request.getSignerUserId().toString(), OPENPGP_ED25519);
            log.info(String.format("Digital signature public key for document %s is %s", version.getId(), singerPublicKey));

            if(singerPublicKey == null) {
                throw new AppException(NotExistError.SINGER_PUBLIC_KEY_EMPTY);
            }

            byte[] detachedSignature = Base64.getDecoder().decode(request.getSignature());
            byte[] fileBytes = minioStorageRest.getObjectBytes(request.getUploadObjectKey());
            if(fileBytes == null){
                throw new AppException(NotExistError.FILE_BYTE_EMPTY);
            }

            fileValidationService.validateFile(fileBytes, doc.getContentType(), doc.getOriginalFilename(), doc.getSizeBytes());

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

            String algorithm = openPgpService.getAlgorithmFromSignature(detachedSignature, singerPublicKey);
            Signature signatureEntity = Signature.builder()
                    .document(doc)
                    .signerUserId(request.getSignerUserId().toString())
                    .signerKeyId(null)
                    .signature(detachedSignature)
                    .algorithm(algorithm)
                    .createdAt(Timestamp.from(Instant.now()))
                    .build();
            signatureRepository.save(signatureEntity);

            if (version.getStatus() == VersionStatus.UPLOADING) {
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
                        .isWatermark(request.getIsWatermark())
                        .build();
                watermarkJobProducer.sendWatermarkJob(jobEvent, doc.getId().toString());
            }

//            // Send malware scan job for async full scan (ClamAV)
            MalwareScanJobEvent scanEvent = MalwareScanJobEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .documentId(String.valueOf(doc.getId()))
                    .versionId(String.valueOf(version.getId()))
                    .ownerId(doc.getOwnerId())
                    .versionNumber(version.getVersionNumber())
                    .uploadObjectKey(request.getUploadObjectKey())
                    .originalFilename(doc.getOriginalFilename())
                    .contentType(doc.getContentType())
                    .sizeBytes(doc.getSizeBytes())
                    .checksum(request.getChecksum()) // Thêm checksum để detect spam
                    .attempt(0)
                    .build();
            malwareScanJobProducer.sendMalwareScanJob(scanEvent, doc.getId().toString());

            return new CompleteUploadResponse(
                    doc.getId(),
                    version.getId(),
                    request.getUploadObjectKey(),
                    request.getChecksum(),
                    version.getSizeBytes(),
                    version.getStatus().toString()
            );
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
                        .request(convertJson(request))
                        .build();

                auditLogProducer.sendAuditLog(logEvent, doc.getId().toString());
            }
        }
    }

    @Override
    @Transactional
    public InitUploadResponse reinitUpload(ReinitUploadRequest request, String userId, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        Document doc = null;
        DocumentVersion version = null;

        try {

            doc = documentRepository.findByIdAndNotDeleted(request.getDocumentId())
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            if (!doc.getOwnerId().equals(userId)) {
                throw new AppException(AuthError.FORBIDDEN_ACTION_UPLOAD);
            }

            version = documentVersionRepository.findById(request.getVersionId())
                    .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

            if (!version.getDocument().getId().equals(doc.getId()) || version.getDeletedAt() != null) {
                throw new AppException(NotExistError.VERSION_NOT_FOUND);
            }

            if (version.getStatus() != VersionStatus.UPLOADING) {
                throw new AppException(ValidationError.CAN_NOT_UPLOAD_BY_STATUS);
            }

            Timestamp createdAt = version.getCreatedAt();
            if (createdAt == null) {
                createdAt = Timestamp.from(Instant.now());
            }

            long minutesSinceCreation = ChronoUnit.MINUTES.between(createdAt.toInstant(), Instant.now());

            boolean fileExists = minioStorageRest.objectExists(version.getStorageObjectKey());

            String objectKey = version.getStorageObjectKey();
            if (!fileExists || minutesSinceCreation >= presignExpiryMinutes) {
                objectKey = String.format("staging/%s/v%d/%s", UUID.randomUUID(), version.getVersionNumber(), UUID.randomUUID());
                version.setStorageObjectKey(objectKey);
                version.setUpdatedAt(Timestamp.from(Instant.now()));
                documentVersionRepository.save(version);
            }

            String preSignedPutUrl = minioStorageRest.generatePreSignedPutUrl(objectKey, presignExpiryMinutes);
            UploadUrlsResponse uploadUrls = new UploadUrlsResponse(objectKey, preSignedPutUrl, null);

            return new InitUploadResponse(
                    doc.getId(),
                    version.getVersionNumber(),
                    uploadUrls,
                    true
            );

        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_REINIT_UPLOAD, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(userId)
                        .action(String.valueOf(ActionLog.INIT_UPLOAD))
                        .documentId(doc.getId().toString())
                        .objectType(DOCUMENT)
                        .status(status)
                        .errorReason(errorReason)
                        .ip(getClientIp(httpRequest))
                        .userAgent(getUserAgent(httpRequest))
                        .metadata(version != null ? convertJson(version.getId().toString()) : null)
                        .request(convertJson(request))
                        .build();

                auditLogProducer.sendAuditLog(logEvent, doc.getId().toString());
            }
        }
    }
}
