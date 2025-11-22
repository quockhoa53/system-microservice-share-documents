package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.DocumentKeyService;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.*;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT_VERSION;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Service
public class DocumentKeyServiceImpl implements DocumentKeyService {

    @Autowired
    private UserKeyRest userKeyRepository;

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private DocumentKeyRepository documentKeyRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private AuditLogProducer auditLogProducer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAndSaveKey(CreateDocumentKeyRequest request, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        DocumentVersion version = null;
        try {
            String publicKey = userKeyRepository.getUserPublicPrimaryKeyForUser(request.getRecipientId(), OPENPGP_CV25519);
            if (publicKey == null) {
                throw new AppException(NotExistError.USER_PUBLIC_KEY_EMPTY);
            }
            version = documentVersionRepository.findById(request.getDocumentVersionId())
                    .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

            byte[] wrapped = openPgpService.wrapCekWithRecipientPublicKey(request.getRawCek(), publicKey);
            DocumentKey key = DocumentKey.builder()
                    .recipientId(request.getRecipientId())
                    .wrappedCek(wrapped)
                    .algorithm(OPENPGP_AES256)
                    .createdAt(Timestamp.from(Instant.now()))
                    .documentVersion(version)
                    .build();
            documentKeyRepository.save(key);
        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_CREATE_KEY, e.getMessage());
        } finally {
            if (version != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(String.valueOf(request.getRecipientId()))
                        .action(String.valueOf(ActionLog.COMPLETE_UPLOAD))
                        .documentId(String.valueOf(version.getDocument().getId()))
                        .objectType(DOCUMENT_VERSION)
                        .status(status)
                        .errorReason(errorReason)
                        .ip(getClientIp(httpRequest))
                        .userAgent(getUserAgent(httpRequest))
                        .metadata(null)
                        .request(String.valueOf(request))
                        .build();

                auditLogProducer.sendAuditLog(logEvent, String.valueOf(version.getDocument().getId()));
            }
        }
    }

    @Transactional(readOnly = true)
    @Override
    public byte[] getDocumentKeyForUser(UUID versionId, String recipientId) {
        return documentKeyRepository
                .findByDocumentVersionIdAndRecipientId(versionId, recipientId)
                .map(key -> {
                    byte[] cek = key.getWrappedCek();
                    if (cek != null) {
                        cek = cek.clone();
                    }
                    return cek;
                })
                .orElse(null);
    }

}
