package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.event.MemberJoinedGroupEvent;
import com.system_share_documents.AppCommonService.rest.grantAccess.GrantAccessRest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.AppCommonService.service.VaultTransitService;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.entity.GroupDocument;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.repository.GroupDocumentRepository;
import com.system_share_documents.DocumentService.service.MemberDocumentKeyEncryptionService;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Clob;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.system_share_documents.AppCommonService.constant.KeysConstant.OPENPGP_CV25519;

/**
 * Service để mã hóa CEK cho member mới join group
 * Tự động mã hóa CEK cho member mới với tất cả documents trong group
 */
@Service
public class MemberDocumentKeyEncryptionServiceImpl implements MemberDocumentKeyEncryptionService {
    private static final Logger log = LoggerFactory.getLogger(MemberDocumentKeyEncryptionServiceImpl.class);

    private final ExecutorService encryptionExecutor = Executors.newFixedThreadPool(4);
    private static final int BATCH_SIZE = 5;

    @Autowired
    private GroupDocumentRepository groupDocumentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentKeyRepository documentKeyRepository;

    @Autowired
    private UserKeyRest userKeyRest;

    @Autowired
    private VaultTransitService vaultTransitService;

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private GrantAccessRest grantAccessRest;

    @Override
    @Transactional(readOnly = true)
    public void encryptCEKForNewMember(MemberJoinedGroupEvent event) throws Exception {
        if (event == null) {
            throw new IllegalArgumentException("MemberJoinedGroupEvent cannot be null");
        }

        String groupId = event.getGroupId();
        String userId = event.getUserId();

        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("GroupId cannot be null or empty");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        log.info("[requestId={}] Starting to encrypt CEK for new member - groupId: {}, userId: {}",
                event.getRequestId(), groupId, userId);

        // 1. Lấy danh sách document IDs trong group
        List<UUID> documentIds = groupDocumentRepository.findDocumentIdsByGroupId(groupId);
        log.info("[requestId={}] Found {} documents in group {}", event.getRequestId(), documentIds.size(), groupId);

        if (documentIds.isEmpty()) {
            log.info("[requestId={}] No documents found in group {}, exiting process", event.getRequestId(), groupId);
            return;
        }

        // 2. Lấy public key của user mới (chỉ lấy một lần)
        String userPublicKey = userKeyRest.getUserPublicPrimaryKeyForUser(userId, OPENPGP_CV25519);
        if (userPublicKey == null || userPublicKey.isBlank()) {
            log.error("[requestId={}] User public key not found for user: {}", event.getRequestId(), userId);
            throw new AppException(NotExistError.USER_PUBLIC_KEY_EMPTY);
        }
        log.info("[requestId={}] Retrieved public key for user {}", event.getRequestId(), userId);

        // 3. Lấy thông tin GroupDocument để biết accessRole cho mỗi document
        // Map documentId -> accessRole
        Map<UUID, String> documentAccessRoleMap = new HashMap<>();
        for (UUID documentId : documentIds) {
            try {
                GroupDocument groupDocument = groupDocumentRepository
                        .findByDocumentIdAndGroupIdAndNotDeleted(documentId, groupId)
                        .orElse(null);
                if (groupDocument != null && groupDocument.getAccessRole() != null) {
                    documentAccessRoleMap.put(documentId, groupDocument.getAccessRole());
                } else {
                    // Default to VIEWER if not found
                    documentAccessRoleMap.put(documentId, "VIEWER");
                }
            } catch (Exception e) {
                log.warn("[requestId={}] Failed to get accessRole for document {}, defaulting to VIEWER: {}",
                        event.getRequestId(), documentId, e.getMessage());
                documentAccessRoleMap.put(documentId, "VIEWER");
            }
        }

        // 4. Với mỗi document, mã hóa CEK cho tất cả versions và cấp quyền
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        for (UUID documentId : documentIds) {
            try {
                log.debug("[requestId={}] Processing document {}", event.getRequestId(), documentId);

                String documentAccessRole = documentAccessRoleMap.getOrDefault(documentId, "VIEWER");

                // Lấy tất cả versions của document với wrappedCEKMaster (sử dụng pagination để tránh memory issues)
                int offset = 0;
                boolean hasMore = true;

                while (hasMore) {
                    List<Object[]> versionRows = documentVersionRepository.getDocumentVersions(
                            documentId.toString(), null, BATCH_SIZE, offset);

                    if (versionRows.isEmpty()) {
                        break;
                    }

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
                                        log.warn("[requestId={}] wrappedCEKMaster is null for version: {}",
                                                event.getRequestId(), versionId);
                                        return null;
                                    }

                                    String wrappedCEKMaster = materializeLob(wrappedCEKMasterObj, versionId);

                                    if (!wrappedCEKMaster.startsWith("vault:")) {
                                        log.warn("[requestId={}] Invalid Vault ciphertext format for version: {}",
                                                event.getRequestId(), versionId);
                                        return null;
                                    }

                                    return new VersionCEK(versionId, wrappedCEKMaster);
                                })
                                .filter(v -> v != null)
                                .collect(Collectors.toList());

                        if (!versionCEKDataList.isEmpty()) {
                            List<CompletableFuture<Void>> batchFutures = processEncryptionBatch(
                                    versionCEKDataList, userId, userPublicKey, event.getRequestId());
                            CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
                            successCount += versionCEKDataList.size();
                        }
                    }

                    hasMore = versionRows.size() == BATCH_SIZE;
                    offset += BATCH_SIZE;
                }

                // Cấp quyền cho group với document này (nếu chưa có)
                // Lưu ý: Quyền cho group thường đã được cấp khi document được thêm vào group
                // Nhưng gọi upsert để đảm bảo quyền đã được cấp đúng
                try {
                    grantAccessRest.upsertGroupDocumentRecipient(documentId.toString(), groupId, documentAccessRole);
                    log.debug("[requestId={}] Upserted group document recipient for document {} and group {} with accessRole: {}",
                            event.getRequestId(), documentId, groupId, documentAccessRole);
                } catch (Exception e) {
                    log.warn("[requestId={}] Failed to upsert group document recipient for document {} and group {}: {}",
                            event.getRequestId(), documentId, groupId, e.getMessage());
                    // Không tăng errorCount vì đây là warning, không phải critical error
                    // Quyền có thể đã được cấp trước đó khi document được thêm vào group
                }
            } catch (Exception e) {
                log.error("[requestId={}] Failed to process document {}: {}",
                        event.getRequestId(), documentId, e.getMessage(), e);
                errorCount++;
            }
        }

        log.info("[requestId={}] Encryption process completed - groupId: {}, userId: {}, success: {}, skipped: {}, errors: {}",
                event.getRequestId(), groupId, userId, successCount, skipCount, errorCount);
    }

    private List<CompletableFuture<Void>> processEncryptionBatch(
            List<VersionCEK> versionCEKDataList, String userId, String userPublicKey, String requestId) {
        return versionCEKDataList.stream()
                .map(versionCEKData -> CompletableFuture.runAsync(() -> {
                    try {
                        encryptCEKForVersion(versionCEKData, userId, userPublicKey, requestId);
                    } catch (Exception e) {
                        log.error("[requestId={}] Failed to encrypt CEK for version {} and user {}: {}",
                                requestId, versionCEKData.getVersionId(), userId, e.getMessage(), e);
                        throw new RuntimeException("Failed to encrypt CEK for version " + versionCEKData.getVersionId() +
                                " and user " + userId + ": " + e.getMessage(), e);
                    }
                }, encryptionExecutor))
                .collect(Collectors.toList());
    }

    private void encryptCEKForVersion(VersionCEK versionCEKData, String userId, String userPublicKey, String requestId) {
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
                // Kiểm tra xem DocumentKey đã tồn tại chưa
                boolean exists = documentKeyRepository
                        .findByDocumentVersionIdAndRecipientId(versionCEKData.getVersionId(), userId)
                        .isPresent();

                if (exists) {
                    log.debug("[requestId={}] DocumentKey already exists for version {} and user {}, skipping",
                            requestId, versionCEKData.getVersionId(), userId);
                    return;
                }

                // Decrypt CEK master từ Vault
                String rawCEKBase64 = vaultTransitService.decrypt(wrappedCEKMaster);
                byte[] rawCEK = Base64.getDecoder().decode(rawCEKBase64);

                if (rawCEK.length != 32) {
                    throw new RuntimeException("Invalid CEK length: " + rawCEK.length + ", expected 32 bytes");
                }

                // Encrypt CEK với public key của user (OpenPGP)
                byte[] wrappedCek = openPgpService.wrapCekWithRecipientPublicKey(userId, rawCEK, userPublicKey);

                // Lưu DocumentKey vào database
                DocumentVersion versionRef = entityManager.getReference(DocumentVersion.class, versionCEKData.getVersionId());
                DocumentKey key = DocumentKey.builder()
                        .documentVersion(versionRef)
                        .recipientId(userId)
                        .wrappedCek(wrappedCek)
                        .algorithm("OPENPGP_AES256")
                        .createdAt(new Timestamp(System.currentTimeMillis()))
                        .build();
                documentKeyRepository.save(key);

                log.debug("[requestId={}] Successfully encrypted and saved CEK for version {} and user {}",
                        requestId, versionCEKData.getVersionId(), userId);
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Failed to encrypt CEK for version " + versionCEKData.getVersionId() +
                        " and user " + userId + ": " + e.getMessage(), e);
            }
        });
    }

    /**
     * Materialize LOB (Clob) thành String
     * Tương tự ShareDocumentServiceImpl.materializeLob()
     */
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

