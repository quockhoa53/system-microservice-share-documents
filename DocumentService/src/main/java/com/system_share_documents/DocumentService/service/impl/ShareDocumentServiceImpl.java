package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.dto.request.GrantAccessRequest;
import com.system_share_documents.AppCommonService.rest.grantAccess.GrantAccessRest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.AppCommonService.service.VaultTransitService;
import com.system_share_documents.DocumentService.dto.request.ShareDocumentRequest;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import com.system_share_documents.DocumentService.service.ShareDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Clob;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_CV25519;

@Service
public class ShareDocumentServiceImpl implements ShareDocumentService {

    private final ExecutorService shareExecutor = Executors.newFixedThreadPool(4);
    private static final int BATCH_SIZE = 5;

    @Autowired
    private DocumentRepository documentRepository;

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

    @Autowired
    private GrantAccessRest grantAccessRest;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    @Transactional(readOnly = false)
    public void shareDocument(ShareDocumentRequest request, HttpServletRequest httpRequest) throws Exception {

        Document document = documentRepository.findByIdAndNotDeleted(UUID.fromString(request.getDocumentId()))
                .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

        int offset = 0;
        boolean hasMore = true;
        boolean hasAnyVersion = false;

        while (hasMore) {

            List<Object[]> versionRows = documentVersionRepository.getDocumentVersions(request.getDocumentId(), null, BATCH_SIZE, offset);

            if (versionRows.isEmpty()) {
                break;
            }

            hasAnyVersion = true;

            List<UUID> versionIds = versionRows.stream()
                    .map(row -> (UUID) row[0])
                    .collect(Collectors.toList());

            List<Object[]> versionCEKRows = documentVersionRepository.findWrappedCEKMasterByIds(versionIds);

            if (!versionCEKRows.isEmpty()) {
                List<VersionCEK> versionCEKDataList = versionCEKRows.stream()
                        .map(row -> {
                            UUID versionId = (UUID) row[0];
                            Object wrappedCEKMasterObj = row[1];

                            if (wrappedCEKMasterObj == null) {
                                throw new RuntimeException("wrappedCEKMaster is null for version: " + versionId);
                            }

                            String wrappedCEKMaster = materializeLob(wrappedCEKMasterObj, versionId);

                            if (!wrappedCEKMaster.startsWith("vault:")) {
                                throw new RuntimeException("Invalid Vault ciphertext format for version: " + versionId +
                                        ". Expected prefix 'vault:' but got: " +
                                        (wrappedCEKMaster.length() > 100 ? wrappedCEKMaster.substring(0, 100) + "..." : wrappedCEKMaster));
                            }

                            return new VersionCEK(versionId, wrappedCEKMaster);
                        })
                        .collect(Collectors.toList());

                List<CompletableFuture<Void>> batchFutures = processShareBatch(versionCEKDataList, request.getRecipients());
                CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
            }

            hasMore = versionRows.size() == BATCH_SIZE;
            offset += BATCH_SIZE;
        }

        if (!hasAnyVersion) {
            throw new AppException(NotExistError.VERSION_NOT_FOUND);
        }

        GrantAccessRequest grantAccessRequest = GrantAccessRequest.builder()
                .documentId(request.getDocumentId())
                .recipients(request.getRecipients().stream()
                        .map(recipient -> {
                            String expirationDays = null;
                            if (recipient.getExpiresAt() != null) {
                                long days = (recipient.getExpiresAt().getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                                if (days > 0) {
                                    expirationDays = String.valueOf(days);
                                }
                            }

                            String accessRole = (recipient.getRole() != null && !recipient.getRole().isEmpty())
                                    ? recipient.getRole()
                                    : "VIEWER";

                            return GrantAccessRequest.AccessRecipientRequest.builder()
                                    .recipientUserId(recipient.getRecipientId())
                                    .accessRole(accessRole)
                                    .expirationDays(expirationDays)
                                    .canDownload(false)
                                    .build();
                        })
                        .collect(Collectors.toList()))
                .build();

        grantAccessRest.createGrantAccess(grantAccessRequest);
    }

    private List<CompletableFuture<Void>> processShareBatch(List<VersionCEK> versionCEKDataList, List<ShareDocumentRequest.ListRecipients> recipients) {
        return versionCEKDataList.stream()
                .flatMap(versionCEKData -> recipients.stream()
                        .map(recipient -> CompletableFuture.runAsync(() -> {
                            try {
                                shareVersionToRecipient(versionCEKData, recipient);
                            } catch (Exception e) {
                                throw new RuntimeException(
                                        "Failed to share document version " + versionCEKData.getVersionId() +
                                                " to " + recipient.getRecipientId() + ": " + e.getMessage(), e);
                            }
                        }, shareExecutor)))
                .collect(Collectors.toList());
    }

    private void shareVersionToRecipient(VersionCEK versionCEKData, ShareDocumentRequest.ListRecipients recipient) {
        String wrappedCEKMaster = versionCEKData.getWrappedCEKMaster();
        if (wrappedCEKMaster == null || wrappedCEKMaster.isEmpty()) {
            throw new RuntimeException("wrappedCEKMaster is null or empty for version: " + versionCEKData.getVersionId());
        }

        if (!wrappedCEKMaster.startsWith("vault:")) {
            throw new RuntimeException("Invalid Vault ciphertext format for version: " + versionCEKData.getVersionId());
        }

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        transactionTemplate.executeWithoutResult(status -> {
            try {
                String rawCEKBase64 = vaultTransitService.decrypt(wrappedCEKMaster);
                byte[] rawCEK = Base64.getDecoder().decode(rawCEKBase64);

                String userPublicKey = userKeyRepository.getUserPublicPrimaryKeyForUser(recipient.getRecipientId(), OPENPGP_CV25519);
                if (userPublicKey == null) {
                    throw new AppException(NotExistError.USER_PUBLIC_KEY_EMPTY);
                }

                byte[] wrappedCek = openPgpService.wrapCekWithRecipientPublicKey(recipient.getRecipientId(), rawCEK, userPublicKey);

                DocumentKey existingKey = documentKeyRepository
                        .findByDocumentVersionIdAndRecipientId(versionCEKData.getVersionId(), recipient.getRecipientId())
                        .orElse(null);

                if (existingKey != null) {
                    existingKey.setWrappedCek(wrappedCek);
                    existingKey.setAlgorithm("OPENPGP_AES256");
                    documentKeyRepository.save(existingKey);
                } else {
                    DocumentVersion versionRef = entityManager.getReference(DocumentVersion.class, versionCEKData.getVersionId());
                    DocumentKey key = DocumentKey.builder()
                            .documentVersion(versionRef)
                            .recipientId(recipient.getRecipientId())
                            .wrappedCek(wrappedCek)
                            .algorithm("OPENPGP_AES256")
                            .createdAt(new java.sql.Timestamp(System.currentTimeMillis()))
                            .build();
                    documentKeyRepository.save(key);
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to share version " + versionCEKData.getVersionId() +
                        " to recipient " + recipient.getRecipientId() + ": " + e.getMessage(), e);
            }
        });
    }

    // Materialize đảm bảo dữ liệu LOB được đọc đầy đủ trong transaction, sau đó dùng dữ liệu đã đọc trong async task mà không cần truy cập database.
    private String materializeLob(Object wrappedCEKMasterObj, UUID versionId) {
        String raw;
        if (wrappedCEKMasterObj instanceof String) {
            raw = (String) wrappedCEKMasterObj;
        } else if (wrappedCEKMasterObj instanceof Clob) {
            try {
                Clob clob = (Clob) wrappedCEKMasterObj;
                long length = clob.length();
                if (length > Integer.MAX_VALUE) {
                    throw new RuntimeException("wrappedCEKMaster too large for version: " + versionId);
                }
                raw = clob.getSubString(1, (int) length);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to read wrappedCEKMaster for version: " + versionId, e);
            }
        } else {
            raw = wrappedCEKMasterObj.toString();
        }

        if (raw.startsWith("vault:")) {
            return raw.trim();
        }
        String trimmed = raw.trim();
        return trimmed.startsWith("vault:") ? trimmed : raw;
    }


    /**
     * DTO để chứa versionId và wrappedCEKMaster đã được materialize
     * Sử dụng để truyền dữ liệu LOB đã được materialize vào async tasks
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VersionCEK {
        private UUID versionId;
        private String wrappedCEKMaster;
    }
}
