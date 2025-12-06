package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CheckAccessRequest;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.rest.grantAccess.GrantAccessRest;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.DocumentService.dto.request.DownLoadDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.DownLoadDocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.service.DocumentKeyService;
import com.system_share_documents.DocumentService.service.DownLoadDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.constant.KeysResponseUtils.CAN_DOWNLOAD;
import static com.system_share_documents.AppCommonService.constant.KeysResponseUtils.DATA;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Service
public class DownLoadDocumentServiceImpl implements DownLoadDocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentKeyService documentKeyService;

    @Autowired
    private MinioStorageRest minioStorageRest;

    @Autowired
    private GrantAccessRest grantAccessRest;

    @Autowired
    private AuditLogProducer auditLogProducer;

    private final int presignExpiryMinutes = 15;

    // Thêm case check thời gian hết hạn quyền download (expirationDays)
    @Override
    public DownLoadDocumentResponse getDownLoadDocument(DownLoadDocumentRequest request, String userId, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        Document doc = null;
        DocumentVersion version = null;
        try {
            doc = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            version = doc.getVersions().stream()
                    .filter(v -> v.getId().equals(request.getVersionId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

            if(!Boolean.TRUE.equals(version.getWatermarked())) {
                throw new AppException(BusinessError.DOCUMENT_NOT_YET_WATERMARK);
            }

            if(version.getStatus() != VersionStatus.AVAILABLE) {
                throw new AppException(BusinessError.DOCUMENT_NOT_YET_AVAILABLE);
            }

            CheckAccessRequest accessRequest = CheckAccessRequest.builder()
                    .documentId(String.valueOf(request.getDocumentId()))
                    .userId(userId)
                    .build();
            HashMap<String, Object> response = grantAccessRest.checkGrantAccess(accessRequest);
            HashMap<String, Object> data = (HashMap<String, Object>) response.get(DATA);
            if (data.get(CAN_DOWNLOAD) == null || Boolean.FALSE.equals(data.get(CAN_DOWNLOAD))) {
                throw new AppException(BusinessError.USER_NOT_PERMISSION);
            }

            byte[] wrappedCek = documentKeyService.getDocumentKeyForUser(request.getVersionId(), userId);
            if (wrappedCek == null) {
                throw new AppException(NotExistError.DOCUMENT_KEY_NOT_FOUND);
            }

            String preSignedUrl = minioStorageRest.generatePreSignedGetUrl(version.getStorageObjectKey(), presignExpiryMinutes);

            return DownLoadDocumentResponse.builder()
                    .documentId(doc.getId())
                    .versionId(version.getId())
                    .encryptedObjectKey(version.getStorageObjectKey())
                    .contentType(doc.getContentType())
                    .checksum(version.getChecksum())
                    .wrappedCek(Base64.getEncoder().encodeToString(wrappedCek))
                    .preSignedUrl(preSignedUrl)
                    .algorithm("AES-256")
                    .expiresAt(Timestamp.from(Instant.now().plusSeconds(presignExpiryMinutes * 60)))
                    .build();

        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_DOWNLOAD_DOCUMENT, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(userId)
                        .action(String.valueOf(ActionLog.DOWNLOAD))
                        .documentId(doc.getId().toString())
                        .objectType("document")
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
