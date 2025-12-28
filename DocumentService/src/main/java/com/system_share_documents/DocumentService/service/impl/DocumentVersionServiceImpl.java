package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.DocumentService.dto.request.DeleteVersionsRequest;
import com.system_share_documents.DocumentService.dto.request.GetListDocumentVersionRequest;
import com.system_share_documents.DocumentService.dto.response.DeleteVersionsResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentVersionResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.AuthError;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.DocumentVersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;
import static com.system_share_documents.AppCommonService.utils.ProcessJsonUtils.convertJson;

@Service
public class DocumentVersionServiceImpl implements DocumentVersionService {

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private AuditLogProducer auditLogProducer;

    @Transactional
    public void createDocumentVersion(Document doc, int versionNumber, String objectKey, long sizeBytes) {
        try {
            DocumentVersion version = DocumentVersion.builder()
                    .document(doc)
                    .versionNumber(versionNumber)
                    .status(VersionStatus.UPLOADING)
                    .storageObjectKey(objectKey)
                    .sizeBytes(sizeBytes)
                    .watermarked(false)
                    .createdAt(Timestamp.from(Instant.now()))
                    .build();
            documentVersionRepository.save(version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DocumentVersionResponse> getDocumentVersion(GetListDocumentVersionRequest request, int page, int size) {
        int limit = size;
        int offset = (page - 1) * size;

        String[] listStatus = null;
        if(request.getStatus() != null && !request.getStatus().isEmpty()) {
            listStatus = request.getStatus().toArray(new String[0]);
        }

        List<Object[]> results = documentVersionRepository.getDocumentVersions(request.getDocumentId(), listStatus, limit, offset);

        return results.stream().map(row -> new DocumentVersionResponse(
                (UUID) row[0],
                (Integer) row[1],
                VersionStatus.valueOf((String) row[2]),
                (String) row[3],
                (String) row[4],
                (Long) row[5],
                (Long) row[6],
                (Timestamp) row[7],
                (Timestamp) row[8]
        )).toList();

    }

    @Override
    public DeleteVersionsResponse deleteVersions(DeleteVersionsRequest request, String userId, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        Document doc = null;
        List<UUID> deletedVersionIds = new ArrayList<>();
        int failedCount = 0;

        try {
            if (request.getVersionIds() == null || request.getVersionIds().isEmpty()) {
                throw new AppException(ValidationError.VERSION_IDS_EMPTY);
            }

            doc = documentRepository.findByIdAndNotDeleted(request.getDocumentId())
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            if (!doc.getOwnerId().equals(userId)) {
                throw new AppException(AuthError.FORBIDDEN_ACTION_DELETE);
            }

            List<UUID> versionIdsToDelete = documentVersionRepository
                    .findIdsByDocumentIdAndIdInAndNotDeleted(request.getDocumentId(), request.getVersionIds());

            if (versionIdsToDelete.isEmpty()) {
                throw new AppException(NotExistError.VERSION_NOT_FOUND);
            }

            Timestamp now = Timestamp.from(Instant.now());

            try {
                int updated = documentVersionRepository.batchSoftDeleteByIds(versionIdsToDelete, now);
                deletedVersionIds.addAll(versionIdsToDelete.subList(0, Math.min(updated, versionIdsToDelete.size())));
                failedCount = versionIdsToDelete.size() - updated;
            } catch (Exception e) {
                // Fallback: xóa từng version một nếu batch delete thất bại
                // Sử dụng update query trực tiếp để tránh load entity (tránh lỗi LOB stream)
                for (UUID versionId : versionIdsToDelete) {
                    try {
                        int updated = documentVersionRepository.softDeleteById(versionId, now);
                        if (updated > 0) {
                            deletedVersionIds.add(versionId);
                        } else {
                            failedCount++;
                        }
                    } catch (Exception ex) {
                        failedCount++;
                    }
                }
            }

            doc.setUpdatedAt(now);
            documentRepository.save(doc);

            int deletedCount = deletedVersionIds.size();

            return DeleteVersionsResponse.builder()
                    .documentId(doc.getId())
                    .deletedVersionIds(deletedVersionIds)
                    .deletedCount(deletedCount)
                    .failedCount(failedCount)
                    .build();

        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_DELETE_VERSION, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(userId)
                        .action(String.valueOf(ActionLog.DELETE_VERSIONS))
                        .documentId(doc.getId().toString())
                        .objectType(DOCUMENT)
                        .status(status)
                        .errorReason(errorReason)
                        .ip(getClientIp(httpRequest))
                        .userAgent(getUserAgent(httpRequest))
                        .metadata(convertJson(deletedVersionIds))
                        .request(convertJson(request))
                        .build();

                auditLogProducer.sendAuditLog(logEvent, doc.getId().toString());
            }
        }
    }
}
