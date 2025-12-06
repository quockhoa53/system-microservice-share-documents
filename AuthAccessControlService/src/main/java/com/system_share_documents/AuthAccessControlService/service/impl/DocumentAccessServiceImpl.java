package com.system_share_documents.AuthAccessControlService.service.impl;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AuthAccessControlService.dto.request.CheckAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GetListRecipientsRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GrantAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.RevokeAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.response.DocumentAccessResponse;
import com.system_share_documents.AuthAccessControlService.entity.DocumentRecipient;
import com.system_share_documents.AuthAccessControlService.enums.DocumentAccessRole;
import com.system_share_documents.AuthAccessControlService.exception.AppException;
import com.system_share_documents.AuthAccessControlService.exception.errorcode.AuthError;
import com.system_share_documents.AuthAccessControlService.exception.errorcode.BusinessError;
import com.system_share_documents.AuthAccessControlService.exception.errorcode.NotExistError;
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
import static com.system_share_documents.AppCommonService.utils.DocumentUtils.checkExistDocument;
import static com.system_share_documents.AppCommonService.utils.DocumentUtils.isDocumentOwnedByUser;
import static com.system_share_documents.AppCommonService.utils.ProcessJsonUtils.convertJson;

@Service
@Slf4j
public class DocumentAccessServiceImpl implements DocumentAccessService {

    private final ExecutorService executor;

    @Autowired
    private RecipientGrantService recipientGrantService;

    @Autowired
    private DocumentRecipientRepository documentRecipientRepository;

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
            Optional<DocumentRecipient> recipientOpt = documentRecipientRepository.findByDocumentIdAndRecipientUserId(request.getDocumentId(), request.getUserId());
            if (recipientOpt.isEmpty()) {
                return DocumentAccessResponse.builder()
                                .documentId(request.getDocumentId())
                                .recipientUserId(request.getUserId())
                                .message("User không có quyền truy cập document này")
                                .build();
            }
            DocumentRecipient recipient = recipientOpt.get();
            return DocumentAccessResponse.builder()
                    .documentId(recipient.getDocumentId())
                    .recipientUserId(recipient.getRecipientUserId())
                    .accessRole(String.valueOf(recipient.getAccessRole()))
                    .canDownload(recipient.getCanDownload())
                    .expiresAt(recipient.getExpiresAt())
                    .grantedAt(recipient.getCreatedAt())
                    .message("User có quyền truy cập document này")
                    .build();
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
}