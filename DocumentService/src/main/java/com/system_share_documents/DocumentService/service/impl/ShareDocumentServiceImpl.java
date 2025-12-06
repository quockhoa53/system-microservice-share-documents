package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.AppCommonService.service.VaultTransitService;
import com.system_share_documents.DocumentService.dto.request.ShareDocumentRequest;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import com.system_share_documents.DocumentService.service.ShareDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_CV25519;

@Service
public class ShareDocumentServiceImpl implements ShareDocumentService {

    private final ExecutorService shareExecutor = Executors.newFixedThreadPool(4);

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentKeyRepository documentKeyRepository;

    @Autowired
    private UserKeyRest userKeyRepository;

    @Autowired
    private VaultTransitService vaultTransitService;

    @Autowired
    private OpenPgpService openPgpService;

    @Override
    public void shareDocument(ShareDocumentRequest request, HttpServletRequest httpRequest) throws Exception {
        DocumentVersion version = documentVersionRepository.findById(UUID.fromString(request.getVersionId()))
                .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

        String rawCEKBase64 = vaultTransitService.decrypt(version.getWrappedCEKMaster());
        byte[] rawCEK = Base64.getDecoder().decode(rawCEKBase64);

        List<CompletableFuture<Void>> futures = request.getRecipients().stream()
                .map(recipient -> CompletableFuture.runAsync(() -> {
                    try {
                        // 3a. Wrap rawCEK bằng public key user
                        String userPublicKey = userKeyRepository.getUserPublicPrimaryKeyForUser(recipient.getRecipientId(), OPENPGP_CV25519);
                        if (userPublicKey == null) {
                            throw new AppException(NotExistError.USER_PUBLIC_KEY_EMPTY);
                        }

                        byte[] wrappedCek = openPgpService.wrapCekWithRecipientPublicKey(recipient.getRecipientId(), rawCEK, userPublicKey);

                        // 3b. Lưu DocumentKey vào DB
                        if (!documentKeyRepository.existsByDocumentVersionIdAndRecipientId(version.getId(), recipient.getRecipientId())) {
                            DocumentKey key = DocumentKey.builder()
                                    .documentVersion(version)
                                    .recipientId(recipient.getRecipientId())
                                    .wrappedCek(wrappedCek)
                                    .algorithm("OPENPGP_AES256")
                                    .createdAt(new java.sql.Timestamp(System.currentTimeMillis()))
                                    .build();
                            documentKeyRepository.save(key);
                        }

                    } catch (Exception e) {
                        throw new RuntimeException("Failed to share document to " + recipient.getRecipientId() + ": " + e.getMessage(), e);
                    }
                }, shareExecutor))
                .toList();

        // 4. Chờ tất cả hoàn thành
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    }
}
