package com.system_share_documents.AuthAccessControlService.service.impl;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.rest.group.GroupRest;
import com.system_share_documents.AuthAccessControlService.dto.request.CheckAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GetListRecipientsRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GrantAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.RevokeAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.response.DocumentAccessResponse;
import com.system_share_documents.AuthAccessControlService.entity.DocumentRecipient;
import com.system_share_documents.AuthAccessControlService.enums.DocumentAccessRole;
import com.system_share_documents.AuthAccessControlService.enums.RecipientType;
import com.system_share_documents.AuthAccessControlService.exception.AppException;
import com.system_share_documents.AuthAccessControlService.exception.errorcode.AuthError;
import com.system_share_documents.AuthAccessControlService.exception.errorcode.BusinessError;
import com.system_share_documents.AuthAccessControlService.repository.DocumentRecipientRepository;
import com.system_share_documents.AuthAccessControlService.service.DocumentAccessService;
import com.system_share_documents.AuthAccessControlService.service.RecipientGrantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static com.system_share_documents.AppCommonService.constant.KeyCloakConstant.GRANT_PASSWORD;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.GRANT_ACCESS;
import static com.system_share_documents.AppCommonService.utils.AuthenticationUtils.getGrantType;
import static com.system_share_documents.AppCommonService.utils.AuthenticationUtils.getUsername;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;
import static com.system_share_documents.AppCommonService.utils.DocumentUtils.isDocumentOwnedByUser;
import static com.system_share_documents.AppCommonService.utils.ProcessJsonUtils.convertJson;
import static com.system_share_documents.AppCommonService.utils.UserCacheUtils.getUserFullName;

@Service
@Slf4j
public class DocumentAccessServiceImpl implements DocumentAccessService {

    private final ExecutorService executor;

    @Autowired
    private RecipientGrantService recipientGrantService;

    @Autowired
    private DocumentRecipientRepository documentRecipientRepository;

    @Autowired
    private GroupRest groupRest;

    @Autowired
    private AuditLogProducer auditLogProducer;

    @Autowired
    public DocumentAccessServiceImpl(ExecutorService commonExecutor) {
        this.executor = commonExecutor;
    }

    @Override
    public List<DocumentAccessResponse> grantAccessDocument(GrantAccessRequest request, HttpServletRequest httpRequest) throws Exception {
        if(getGrantType(httpRequest) != null && Objects.equals(getGrantType(httpRequest), GRANT_PASSWORD)) {
            if(!isDocumentOwnedByUser(getUsername(), request.getDocumentId())) {
                throw new AppException(AuthError.GRANT_FORBIDDEN);
            }
        }
        String status = "OK";
        String errorReason = null;
        List<DocumentAccessResponse> responses = new ArrayList<>();
        List<String> successUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        String documentId = request.getDocumentId();
        try {
            List<CompletableFuture<DocumentAccessResponse>> futures = request.getRecipients()
                    .stream()
                    .map(recipient -> CompletableFuture.supplyAsync(() ->
                            recipientGrantService.processGrantAccessForRecipient(documentId, recipient), executor))
                    .toList();

            responses = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            for (DocumentAccessResponse r : responses) {
                if (r.getMessage().contains("thành công")) {
                    successUsers.add(r.getRecipientUserId());
                } else {
                    failedUsers.add(r.getRecipientUserId());
                }
            }
            return responses;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_GRANT_ACCESS, e.getMessage());
        } finally {
            List<String> affectedUsers = request.getRecipients()
                    .stream()
                    .map(GrantAccessRequest.AccessRecipientRequest::getRecipientUserId)
                    .toList();

            HashMap<String, Object> metadata = new HashMap<>();
            metadata.put("successUsers", successUsers);
            metadata.put("failedUsers", failedUsers);
            metadata.put("responses", responses);

            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(getUsername())
                    .affectedUsers(convertJson(affectedUsers))
                    .action(String.valueOf(ActionLog.GRANT_ACCESS))
                    .documentId(documentId)
                    .objectType("document")
                    .status(status)
                    .errorReason(errorReason)
                    .ip(getClientIp(httpRequest))
                    .userAgent(getUserAgent(httpRequest))
                    .metadata(convertJson(metadata))
                    .request(convertJson(request))
                    .build();

            auditLogProducer.sendAuditLog(logEvent, documentId);
        }
    }

    @Override
    @Transactional
    public List<DocumentAccessResponse> revokeGrantAccessDocument(RevokeAccessRequest request, HttpServletRequest httpRequest) throws Exception {
        if(!isDocumentOwnedByUser(getUsername(), request.getDocumentId())) {
            throw new AppException(AuthError.GRANT_FORBIDDEN);
        }
        String status = "OK";
        String errorReason = null;
        List<DocumentAccessResponse> responses = new ArrayList<>();
        List<String> successUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
        try {
            List<String> targetUsers;
            if ("ALL".equalsIgnoreCase(request.getType())) {
                targetUsers = documentRecipientRepository.findAllByDocumentId(request.getDocumentId())
                        .stream()
                        .map(com.system_share_documents.AuthAccessControlService.entity.DocumentRecipient::getRecipientUserId)
                        .toList();

                if (targetUsers.isEmpty()) {
                    return Collections.emptyList();
                }
            } else {
                targetUsers = request.getRecipientUserIds();
            }

            documentRecipientRepository.bulkRevokeAccess(request.getDocumentId(), targetUsers, DocumentAccessRole.REVOKED, currentTimestamp);

            for (String userId : targetUsers) {
                DocumentAccessResponse response = DocumentAccessResponse.builder()
                        .documentId(request.getDocumentId())
                        .recipientUserId(userId)
                        .message("Thu hồi quyền truy cập thành công")
                        .accessRole(String.valueOf(DocumentAccessRole.REVOKED))
                        .canDownload(false)
                        .build();

                responses.add(response);
                successUsers.add(userId);
            }

            return responses;

        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_REVOKE_GRANT, e.getMessage());
        } finally {
            List<String> affectedUsers;
            if ("ALL".equalsIgnoreCase(request.getType())) {
                affectedUsers = successUsers;
            } else {
                affectedUsers = request.getRecipientUserIds() != null ? request.getRecipientUserIds() : new ArrayList<>();
            }

            HashMap<String, Object> metadata = new HashMap<>();
            metadata.put("successUsers", successUsers);
            metadata.put("failedUsers", failedUsers);
            metadata.put("responses", responses);

            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(getUsername())
                    .affectedUsers(convertJson(affectedUsers))
                    .action(String.valueOf(ActionLog.REVOKE_GRANT_ACCESS))
                    .documentId(request.getDocumentId())
                    .objectType("document")
                    .status(status)
                    .errorReason(errorReason)
                    .ip(getClientIp(httpRequest))
                    .userAgent(getUserAgent(httpRequest))
                    .metadata(convertJson(metadata))
                    .request(convertJson(request))
                    .build();

            auditLogProducer.sendAuditLog(logEvent, request.getDocumentId());
        }
    }

    @Override
    public List<DocumentAccessResponse> listRecipientsDocument(GetListRecipientsRequest request, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        List<DocumentAccessResponse> responses = new ArrayList<>();
        List<String> users = new ArrayList<>();
        try {
            List<DocumentRecipient> recipients;
            if (request.getType() != null) {
                recipients = documentRecipientRepository.findAllByDocumentIdAndAccessRole(request.getDocumentId(), DocumentAccessRole.valueOf(request.getType()));
            } else {
                recipients = documentRecipientRepository.findAllByDocumentId(request.getDocumentId());
            }
            for(DocumentRecipient recipient : recipients) {
                responses.add(DocumentAccessResponse.builder()
                        .documentId(request.getDocumentId())
                        .recipientUserId(recipient.getRecipientUserId())
                        .fullName(getUserFullName(recipient.getRecipientUserId()))
                        .accessRole(String.valueOf(recipient.getAccessRole()))
                        .canDownload(recipient.getCanDownload())
                        .expiresAt(recipient.getExpiresAt())
                        .grantedAt(recipient.getCreatedAt())
                        .build());
                users.add(recipient.getRecipientUserId());
            }
            return responses;
        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_GET_RECIPIENTS, e.getMessage());
        } finally {
            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(getUsername())
                    .affectedUsers(convertJson(users))
                    .action(String.valueOf(ActionLog.VIEW))
                    .documentId(request.getDocumentId())
                    .objectType(GRANT_ACCESS)
                    .status(status)
                    .errorReason(errorReason)
                    .ip(getClientIp(httpRequest))
                    .userAgent(getUserAgent(httpRequest))
                    .metadata(null)
                    .request(convertJson(request))
                    .build();

            auditLogProducer.sendAuditLog(logEvent, request.getDocumentId());
        }
    }

    @Override
    public DocumentAccessResponse checkAccessDocument(CheckAccessRequest request, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        try {
            // Chỉ check DocumentRecipient (bỏ ACL)
            return checkAccessFromDocumentRecipient(request);
        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_CHECK_GRANT, e.getMessage());
        } finally {
            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(getUsername())
                    .affectedUsers(request.getUserId())
                    .action(String.valueOf(ActionLog.CHECK))
                    .documentId(request.getDocumentId())
                    .objectType(GRANT_ACCESS)
                    .status(status)
                    .errorReason(errorReason)
                    .ip(getClientIp(httpRequest))
                    .userAgent(getUserAgent(httpRequest))
                    .metadata(null)
                    .request(convertJson(request))
                    .build();

            auditLogProducer.sendAuditLog(logEvent, request.getDocumentId());
        }
    }

    /**
     * Check quyền từ DocumentRecipient
     * Hỗ trợ cả USER và GROUP recipients
     */
    private DocumentAccessResponse checkAccessFromDocumentRecipient(CheckAccessRequest request) {
        String documentId = request.getDocumentId();
        String userId = request.getUserId();
        Boolean isGroup = request.getIsGroup();

        // Nếu isGroup = true: chỉ check GROUP recipients
        if (Boolean.TRUE.equals(isGroup)) {
            return checkGroupRecipients(documentId, userId);
        }

        // Nếu isGroup = false: chỉ check USER recipient
        if (Boolean.FALSE.equals(isGroup)) {
            return checkUserRecipient(documentId, userId);
        }

        // Nếu isGroup = null: check cả hai, ưu tiên USER trước
        DocumentAccessResponse userResponse = checkUserRecipient(documentId, userId);
        if (userResponse.getAccessRole() != null) {
            return userResponse;
        }

        return checkGroupRecipients(documentId, userId);
    }

    /**
     * Check quyền từ USER recipient (direct share)
     */
    private DocumentAccessResponse checkUserRecipient(String documentId, String userId) {
        Optional<DocumentRecipient> userRecipientOpt = documentRecipientRepository.findByDocumentIdAndRecipientUserIdAndRecipientType(documentId, userId, RecipientType.USER);

        if (userRecipientOpt.isEmpty()) {
            return DocumentAccessResponse.builder()
                    .documentId(documentId)
                    .recipientUserId(userId)
                    .message("User không có quyền truy cập document này (direct share)")
                    .build();
        }

        DocumentRecipient recipient = userRecipientOpt.get();

        // Nếu bị revoke, không có quyền
        if (Boolean.TRUE.equals(recipient.getIsRevoke())) {
            return DocumentAccessResponse.builder()
                    .documentId(documentId)
                    .recipientUserId(userId)
                    .message("User bị chặn truy cập document này")
                    .build();
        }

        // Check expiry
        if (recipient.getExpiresAt() != null && recipient.getExpiresAt().before(new Timestamp(System.currentTimeMillis()))) {
            return DocumentAccessResponse.builder()
                    .documentId(documentId)
                    .recipientUserId(userId)
                    .message("Quyền truy cập đã hết hạn")
                    .build();
        }

        return DocumentAccessResponse.builder()
                .documentId(documentId)
                .recipientUserId(userId)
                .accessRole(String.valueOf(recipient.getAccessRole()))
                .canDownload(recipient.getCanDownload())
                .expiresAt(recipient.getExpiresAt())
                .grantedAt(recipient.getCreatedAt())
                .message("User có quyền truy cập document này (direct share)")
                .build();
    }

    /**
     * Check quyền từ GROUP recipients
     */
    private DocumentAccessResponse checkGroupRecipients(String documentId, String userId) {
        List<DocumentRecipient> allRecipients = documentRecipientRepository.findAllByDocumentId(documentId);
        List<DocumentRecipient> groupRecipients = allRecipients.stream()
                .filter(r -> RecipientType.GROUP.equals(r.getRecipientType()))
                .filter(r -> !Boolean.TRUE.equals(r.getIsRevoke()))
                .filter(r -> r.getExpiresAt() == null || r.getExpiresAt().after(new Timestamp(System.currentTimeMillis())))
                .collect(Collectors.toList());

        if (groupRecipients.isEmpty()) {
            return DocumentAccessResponse.builder()
                    .documentId(documentId)
                    .recipientUserId(userId)
                    .message("Không có group nào được cấp quyền truy cập document này")
                    .build();
        }

        // Check xem user có là member của group nào không
        for (DocumentRecipient groupRecipient : groupRecipients) {
            String groupId = groupRecipient.getRecipientGroupId();
            if (groupId != null) {
                try {
                    UUID groupUuid = UUID.fromString(groupId);
                    UUID userUuid = UUID.fromString(userId);
                    Map<String, Object> membership = groupRest.checkMembership(groupUuid, userUuid);

                    if (membership != null && Boolean.TRUE.equals(membership.get("member"))) {
                        // User là member của group này, có quyền truy cập
                        return DocumentAccessResponse.builder()
                                .documentId(documentId)
                                .recipientUserId(userId)
                                .accessRole(String.valueOf(groupRecipient.getAccessRole()))
                                .canDownload(groupRecipient.getCanDownload())
                                .expiresAt(groupRecipient.getExpiresAt())
                                .grantedAt(groupRecipient.getCreatedAt())
                                .message("User có quyền truy cập document này (từ group)")
                                .build();
                    }
                } catch (Exception e) {
                    // Log error nhưng tiếp tục check các groups khác
                    log.warn("Error checking membership for group {} and user {}: {}", groupId, userId, e.getMessage());
                }
            }
        }

        // Không có quyền truy cập từ bất kỳ group nào
        return DocumentAccessResponse.builder()
                .documentId(documentId)
                .recipientUserId(userId)
                .message("User không phải member của bất kỳ group nào được cấp quyền")
                .build();
    }

    /**
     * Internal API: Tạo hoặc cập nhật DocumentRecipient cho group document
     * Được gọi từ DocumentService khi thêm/cập nhật document vào group
     */
    @Override
    @Transactional
    public void upsertGroupDocumentRecipient(String documentId, String groupId, String accessRole) throws Exception {
        // Map AccessRoleGroupDocument sang DocumentAccessRole
        DocumentAccessRole documentAccessRole = mapGroupAccessRoleToDocumentAccessRole(accessRole);

        // Tìm DocumentRecipient hiện có cho group này
        Optional<DocumentRecipient> existingRecipientOpt = documentRecipientRepository
                .findByDocumentIdAndRecipientGroupIdAndRecipientType(documentId, groupId, RecipientType.GROUP);

        Timestamp now = new Timestamp(System.currentTimeMillis());

        if (existingRecipientOpt.isPresent()) {
            // Cập nhật accessRole nếu đã tồn tại
            DocumentRecipient recipient = existingRecipientOpt.get();
            boolean canDownload = documentAccessRole == DocumentAccessRole.DOWNLOADER || documentAccessRole == DocumentAccessRole.EDITOR || documentAccessRole == DocumentAccessRole.OWNER;
            recipient.setAccessRole(documentAccessRole);
            recipient.setUpdatedAt(now);
            recipient.setIsRevoke(false);// Reset revoke flag nếu có
            recipient.setCanDownload(canDownload);
            documentRecipientRepository.save(recipient);
            log.debug("Updated DocumentRecipient for group {} and document {}", groupId, documentId);
        } else {
            // Tạo mới DocumentRecipient cho group
            DocumentRecipient recipient = DocumentRecipient.builder()
                    .documentId(documentId)
                    .recipientType(RecipientType.GROUP)
                    .recipientGroupId(groupId)
                    .recipientUserId(null) // null cho GROUP type
                    .accessRole(documentAccessRole)
                    .canDownload(documentAccessRole == DocumentAccessRole.DOWNLOADER ||
                            documentAccessRole == DocumentAccessRole.EDITOR ||
                            documentAccessRole == DocumentAccessRole.OWNER)
                    .isRevoke(false)
                    .createdAt(now)
                    .updatedAt(now)
                    .expiresAt(null) // Không có expiry cho group permissions
                    .build();
            documentRecipientRepository.save(recipient);
            log.debug("Created DocumentRecipient for group {} and document {}", groupId, documentId);
        }
    }

    /**
     * Map AccessRoleGroupDocument (từ GroupDocument) sang DocumentAccessRole (cho DocumentRecipient)
     */
    private DocumentAccessRole mapGroupAccessRoleToDocumentAccessRole(String groupAccessRole) {
        if (groupAccessRole == null || groupAccessRole.isBlank()) {
            return DocumentAccessRole.VIEWER;
        }

        String role = groupAccessRole.toUpperCase().trim();
        switch (role) {
            case "VIEWER":
                return DocumentAccessRole.VIEWER;
            case "SHARE":
                // SHARE có thể view và share, map sang VIEWER hoặc EDITOR
                return DocumentAccessRole.VIEWER;
            case "DOWNLOAD":
                return DocumentAccessRole.DOWNLOADER;
            case "ADMIN":
                // ADMIN có thể edit, map sang EDITOR
                return DocumentAccessRole.EDITOR;
            case "REVOKE":
                return DocumentAccessRole.REVOKED;
            default:
                log.warn("Unknown group access role: {}, defaulting to VIEWER", groupAccessRole);
                return DocumentAccessRole.VIEWER;
        }
    }
}