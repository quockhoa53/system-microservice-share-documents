package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.service.DocumentKeyService;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import java.sql.Timestamp;
import java.time.Instant;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_AES256;
import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_ED25519;

@Service
public class DocumentKeyServiceImpl implements DocumentKeyService {

    @Autowired
    private UserKeyRest userKeyRepository;

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private DocumentKeyRepository documentKeyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createAndSaveKey(String recipient, DocumentVersion version, byte[] cekBytes) throws Exception {
        String publicKey = userKeyRepository.getUserPublicPrimaryKeyForUser(recipient, OPENPGP_ED25519);
        if (publicKey != null && !publicKey.isBlank()) {
            byte[] wrapped = openPgpService.wrapCekWithRecipientPublicKey(cekBytes, publicKey);
            DocumentKey key = DocumentKey.builder()
                    .recipientId(recipient)
                    .wrappedCek(wrapped)
                    .algorithm(OPENPGP_AES256)
                    .createdAt(Timestamp.from(Instant.now()))
                    .documentVersion(version)
                    .build();
            documentKeyRepository.save(key);
        }
    }

}
