package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.AppCommonService.service.VaultTransitService;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.DocumentKeyService;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.*;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT_VERSION;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Service
public class DocumentKeyServiceImpl implements DocumentKeyService {

    private static final Logger log = LoggerFactory.getLogger(DocumentKeyServiceImpl.class);

    @Autowired
    private UserKeyRest userKeyRepository;

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private VaultTransitService vaultTransitService;

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
            version = documentVersionRepository.findById(request.getDocumentVersionId())
                    .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

            String wrappedMaster = new String(request.getWrappedByVault(), StandardCharsets.UTF_8);
            String rawCEKBase64 = vaultTransitService.decrypt(wrappedMaster);
            byte[] rawCEK = Base64.getDecoder().decode(rawCEKBase64);
            version.setWrappedCEKMaster(wrappedMaster);
            documentVersionRepository.save(version);

            if (rawCEK.length != 32) {
                throw new AppException(BusinessError.FAILED_CREATE_KEY,
                        String.format("CEK length is %d bytes, expected 32 bytes for AES-256. This indicates an issue with key generation or Vault encryption/decryption.", rawCEK.length));
            }

            String publicKey = userKeyRepository.getUserPublicPrimaryKeyForUser(request.getRecipientId(), OPENPGP_CV25519);
            log.info(String.format("Public key used to decrypt document %s is %s", request.getDocumentVersionId(), publicKey));

            if (publicKey == null) {
                throw new AppException(NotExistError.USER_PUBLIC_KEY_EMPTY);
            }

            if (documentKeyRepository.existsByDocumentVersionIdAndRecipientId(request.getDocumentVersionId(), request.getRecipientId())) {
                return;
            }

            byte[] wrapped = openPgpService.wrapCekWithRecipientPublicKey(request.getRecipientId(), rawCEK, publicKey);
            DocumentKey key = DocumentKey.builder()
                    .wrappedCek(wrapped)
                    .algorithm(OPENPGP_AES256)
                    .createdAt(Timestamp.from(Instant.now()))
                    .documentVersion(version)
                    .recipientId(request.getRecipientId())
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
