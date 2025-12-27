package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CheckAccessRequest;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.rest.grantAccess.GrantAccessRest;
import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.DocumentService.dto.request.PreviewDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.PreviewDocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.DocumentKeyService;
import com.system_share_documents.DocumentService.service.PreviewDocumentService;
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
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;
import static com.system_share_documents.AppCommonService.utils.ProcessJsonUtils.convertJson;

@Service
public class PreviewDocumentServiceImpl implements PreviewDocumentService {

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private GrantAccessRest grantAccessRest;

    @Autowired
    private MinioStorageRest minioStorageRest;

    @Autowired
    private AuditLogProducer auditLogProducer;

    @Autowired
    private DocumentKeyService documentKeyService;

    private final int presignExpiryMinutes = 15;

    @Override
    public PreviewDocumentResponse previewDocument(PreviewDocumentRequest request, String userId, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        Document doc = null;
        DocumentVersion version = null;

        try {

            doc = documentRepository.findByIdAndNotDeleted(request.getDocumentId())
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            if (request.getVersionId() != null) {
                version = documentVersionRepository.findById(request.getVersionId())
                        .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

                if (!version.getDocument().getId().equals(doc.getId()) || version.getDeletedAt() != null) {
                    throw new AppException(NotExistError.VERSION_NOT_FOUND);
                }
            } else {
                version = documentVersionRepository.findLatestVersionByDocumentId(doc.getId())
                        .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));
            }

            if (version.getStatus() != VersionStatus.AVAILABLE) {
                throw new AppException(BusinessError.DOCUMENT_NOT_YET_AVAILABLE);
            }

            boolean hasAccess = false;

            // 1. Check nếu là owner
            if (doc.getOwnerId().equals(userId)) {
                hasAccess = true;
            } else {
                // 2. Check direct access - chỉ cần có access (không cần check canDownload)
                // Preview chỉ cần quyền VIEW, không cần quyền DOWNLOAD
                CheckAccessRequest accessRequest = CheckAccessRequest.builder()
                        .documentId(String.valueOf(request.getDocumentId()))
                        .userId(userId)
                        .isGroup(request.getIsGroup() != null ? request.getIsGroup() : false)
                        .build();
                HashMap<String, Object> accessResponse = grantAccessRest.checkGrantAccess(accessRequest);
                HashMap<String, Object> data = (HashMap<String, Object>) accessResponse.get(DATA);
                // Nếu có accessRole trong data nghĩa là user có access (dù canDownload là true hay false)
                // Nếu không có accessRole hoặc message là "User không có quyền truy cập" thì không có access
                if (data != null) {
                    String accessRole = (String) data.get("accessRole");
                    String message = (String) data.get("message");
                    // Có access nếu có accessRole và không phải là message "không có quyền"
                    if (accessRole != null && !accessRole.isEmpty() &&
                            (message == null || !message.contains("không có quyền"))) {
                        hasAccess = true;
                    }
                }
            }

            if (!hasAccess) {
                throw new AppException(BusinessError.USER_NOT_PERMISSION);
            }

            // Lấy wrappedCek để decrypt file
            byte[] wrappedCek = documentKeyService.getDocumentKeyForUser(version.getId(), userId);
            if (wrappedCek == null) {
                throw new AppException(NotExistError.DOCUMENT_KEY_NOT_FOUND);
            }

            if (!minioStorageRest.objectExists(version.getStorageObjectKey())) {
                throw new AppException(NotExistError.FILE_NOT_FOUND);
            }

            String preSignedUrl = minioStorageRest.generatePreSignedGetUrl(version.getStorageObjectKey(), presignExpiryMinutes);

            return PreviewDocumentResponse.builder()
                    .documentId(doc.getId())
                    .versionId(version.getId())
                    .preSignedUrl(preSignedUrl)
                    .contentType(doc.getContentType())
                    .sizeBytes(version.getSizeBytes())
                    .expiresAt(Timestamp.from(Instant.now().plusSeconds(presignExpiryMinutes * 60L)))
                    .wrappedCek(Base64.getEncoder().encodeToString(wrappedCek))
                    .build();

        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_PREVIEW_DOCUMENT, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(userId)
                        .action(String.valueOf(ActionLog.PREVIEW))
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
