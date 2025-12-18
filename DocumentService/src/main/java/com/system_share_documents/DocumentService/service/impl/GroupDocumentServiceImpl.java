package com.system_share_documents.DocumentService.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.system_share_documents.AppCommonService.rest.group.GroupRest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.AppCommonService.service.VaultTransitService;
import com.system_share_documents.DocumentService.dto.request.AddDocumentToGroupRequest;
import com.system_share_documents.DocumentService.dto.request.GetGroupDocumentsRequest;
import com.system_share_documents.DocumentService.dto.request.RemoveDocumentFromGroupRequest;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentDetailResponse;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.GroupDocument;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.*;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.repository.GroupDocumentRepository;
import com.system_share_documents.DocumentService.service.DocumentKeyService;
import com.system_share_documents.DocumentService.service.GroupDocumentService;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import com.system_share_documents.DocumentService.utils.MapperUtils;
import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.sql.Timestamp;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GroupDocumentServiceImpl implements GroupDocumentService {

    @Autowired
    private GroupDocumentRepository groupDocumentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentKeyRepository documentKeyRepository;

    @Autowired
    private GroupRest groupRest;

    @Autowired
    private UserKeyRest userKeyRest;

    @Autowired
    private VaultTransitService vaultTransitService;

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private DocumentKeyService documentKeyService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    private MapperUtils mapperUtils;

    private final ExecutorService encryptionExecutor = Executors.newFixedThreadPool(4);

    @Override
    @Transactional
    public GroupDocumentResponse addDocumentToGroup(AddDocumentToGroupRequest request, String userId, HttpServletRequest httpRequest) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(AuthError.UNAUTHORIZED, "User not authenticated");
        }

        UUID documentId = UUID.fromString(request.getDocumentId());
        String groupId = request.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new AppException(ValidationError.INVALID_PARAM, "groupId is required");
        }

        Document document = documentRepository.findByIdAndNotDeleted(documentId)
                .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

        if (!document.getOwnerId().equals(userId)) {
            throw new AppException(AuthError.FORBIDDEN, "Only document owner can add document to group");
        }

        //---- Check tồn tại group, lấy groupdetail từ cache
//        Map<String, Object> groupDetail = groupRest.getGroupDetail(groupUuid);
//        if (groupDetail == null) {
//            throw new AppException(NotExistError.NOT_FOUND, "Group not found");
//        }

        UUID groupUuid = UUID.fromString(groupId);

        Map<String, Object> membership = groupRest.checkMembership(groupUuid, UUID.fromString(userId));
        if (membership == null || !Boolean.TRUE.equals(membership.get("member"))) {
            throw new AppException(AuthError.FORBIDDEN, "You are not a member of this group");
        }

        String role = (String) membership.get("role");
        if (role == null || (!role.equalsIgnoreCase("owner") && !role.equalsIgnoreCase("admin"))) {
            throw new AppException(AuthError.FORBIDDEN, "Only group owner/admin can add documents to group");
        }

        if (groupDocumentRepository.existsByDocumentIdAndGroupIdAndNotDeleted(documentId, groupId)) {
            throw new AppException(NotExistError.ALREADY_EXITS, "Document already exists in this group");
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        String accessRole = String.valueOf(request.getAccessRole());
        if (accessRole == null || accessRole.isBlank()) {
            accessRole = "VIEWER";
        }

        GroupDocument groupDocument = GroupDocument.builder()
                .documentId(documentId)
                .groupId(groupId)
                .addedBy(userId)
                .accessRole(accessRole)
                .createdAt(now)
                .updatedAt(now)
                .build();

        GroupDocument saved = groupDocumentRepository.save(groupDocument);

        try {
            encryptCEKForAllGroupMembers(documentId, groupUuid, httpRequest);
        } catch (Exception e) {
            // Log error nhưng không throw để không fail việc thêm document vào group
            // DocumentKey sẽ được tạo lazy khi member truy cập
            System.err.println("Warning: Failed to encrypt CEK for group members: " + e.getMessage());
        }

        // Build response
//        String groupName = (String) groupDetail.get("name");
        return GroupDocumentResponse.builder()
                .id(saved.getId())
                .documentId(saved.getDocumentId())
                .documentName(document.getOriginalFilename())
                .groupId(saved.getGroupId())
                .addedBy(saved.getAddedBy())
                .accessRole(saved.getAccessRole())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public void removeDocumentFromGroup(RemoveDocumentFromGroupRequest request, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(AuthError.UNAUTHORIZED, "User not authenticated");
        }

        UUID documentId = UUID.fromString(request.getDocumentId());
        String groupId = request.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new AppException(ValidationError.INVALID_PARAM, "groupId is required");
        }

        GroupDocument groupDocument = groupDocumentRepository
                .findByDocumentIdAndGroupIdAndNotDeleted(documentId, groupId)
                .orElseThrow(() -> new AppException(NotExistError.GROUP_DOCUMENT_NOT_FOUND));

        Document document = documentRepository.findByIdAndNotDeleted(documentId)
                .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

        UUID groupUuid = UUID.fromString(groupId);
        Map<String, Object> membership = groupRest.checkMembership(groupUuid, UUID.fromString(userId));

        boolean isDocumentOwner = document.getOwnerId().equals(userId);
        String memberRole = membership != null ? (String) membership.get("role") : null;
        boolean isGroupOwnerOrAdmin = membership != null &&
                Boolean.TRUE.equals(membership.get("member")) &&
                (memberRole != null && (memberRole.equals("owner") || memberRole.equals("admin")));

        if (!isDocumentOwner && !isGroupOwnerOrAdmin) {
            throw new AppException(AuthError.FORBIDDEN, "Only document owner or group owner/admin can remove document from group");
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        groupDocumentRepository.softDeleteByDocumentIdAndGroupId(documentId, groupId, now, now);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GroupDocumentDetailResponse> getGroupDocuments(GetGroupDocumentsRequest request, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(AuthError.UNAUTHORIZED, "User not authenticated");
        }

        String groupId = request.getGroupId();
        if (groupId == null || groupId.isBlank()) {
            throw new AppException(ValidationError.INVALID_PARAM, "groupId is required");
        }

        // Kiểm tra group tồn tại và user có quyền xem
        UUID groupUuid = UUID.fromString(groupId);

//        Map<String, Object> groupDetail = groupRest.getGroupDetail(groupUuid);
//        if (groupDetail == null) {
//            throw new AppException(NotExistError.NOT_FOUND, "Group not found");
//        }

        // Kiểm tra user có phải là member của group không
        Map<String, Object> membership = groupRest.checkMembership(groupUuid, UUID.fromString(userId));
        if (membership == null || !Boolean.TRUE.equals(membership.get("member"))) {
            throw new AppException(AuthError.FORBIDDEN, "You are not a member of this group");
        }

        // Phân trang
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        Pageable pageable = PageRequest.of(page, size);

        Page<GroupDocument> groupDocuments = groupDocumentRepository.findByGroupIdAndNotDeleted(groupId, pageable);

        List<UUID> documentIds = groupDocuments.getContent().stream()
                .map(GroupDocument::getDocumentId)
                .collect(Collectors.toList());

        Map<UUID, Document> documentMap;
        if (!documentIds.isEmpty()) {
            List<Document> documents = documentRepository.findAllByIdIn(documentIds);
            documentMap = documents.stream()
                    .collect(Collectors.toMap(Document::getId, doc -> doc));
        } else {
            documentMap = new HashMap<>();
        }

//        // Lấy thông tin users (addedBy) - có thể cache hoặc batch query
//        String groupName = (String) groupDetail.get("name");
//        final String finalGroupName = groupName;

        // Map sang response
        return groupDocuments.map(gd -> {
            Document doc = documentMap.get(gd.getDocumentId());
            DocumentResponse docResponse = null;
            try {
                docResponse = doc != null ? mapperUtils.mapDocumentEntityToResponse(doc) : null;
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            return GroupDocumentDetailResponse.builder()
                    .id(gd.getId())
                    .document(docResponse)
                    .groupId(gd.getGroupId())
                    .addedBy(gd.getAddedBy())
                    .addedByName(null)
                    .accessRole(gd.getAccessRole())
                    .createdAt(gd.getCreatedAt())
                    .updatedAt(gd.getUpdatedAt())
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupDocumentResponse> getDocumentGroups(UUID documentId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(AuthError.UNAUTHORIZED, "User not authenticated");
        }

        Document document = documentRepository.findByIdAndNotDeleted(documentId)
                .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

        if (!document.getOwnerId().equals(userId)) {
            throw new AppException(AuthError.FORBIDDEN, "Only document owner can view document groups");
        }

        // Lấy danh sách GroupDocument
        List<GroupDocument> groupDocuments = groupDocumentRepository.findByDocumentIdAndNotDeleted(documentId);

//        // Lấy thông tin groups (có thể batch query để tối ưu)
//        Map<String, String> groupNameMap = new HashMap<>();
//        for (GroupDocument gd : groupDocuments) {
//            try {
//                UUID groupUuid = UUID.fromString(gd.getGroupId());
//                Map<String, Object> groupDetail = groupRest.getGroupDetail(groupUuid);
//                if (groupDetail != null) {
//                    groupNameMap.put(gd.getGroupId(), (String) groupDetail.get("name"));
//                }
//            } catch (IllegalArgumentException e) {
//                // Invalid UUID format từ database - skip group này
//                System.err.println("Invalid groupId format: " + gd.getGroupId());
//            } catch (Exception e) {
//                // Log error nhưng không throw
//                System.err.println("Error fetching group detail for " + gd.getGroupId() + ": " + e.getMessage());
//            }
//        }

        // Map sang response
//        final Map<String, String> finalGroupNameMap = groupNameMap;
        return groupDocuments.stream()
                .map(gd -> GroupDocumentResponse.builder()
                        .id(gd.getId())
                        .documentId(gd.getDocumentId())
                        .documentName(document.getOriginalFilename())
                        .groupId(gd.getGroupId())
//                        .groupName(finalGroupNameMap.getOrDefault(gd.getGroupId(), "Unknown Group"))
                        .addedBy(gd.getAddedBy())
                        .accessRole(gd.getAccessRole())
                        .createdAt(gd.getCreatedAt())
                        .updatedAt(gd.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkDocumentGroupAccess(UUID documentId, String userId) {

        List<String> groupIds = groupDocumentRepository.findGroupIdsByDocumentId(documentId);

        if (groupIds.isEmpty()) {
            return false;
        }

        UUID userUuid = UUID.fromString(userId);
        for (String groupId : groupIds) {
            try {
                UUID groupUuid = UUID.fromString(groupId);
                Map<String, Object> membership = groupRest.checkMembership(groupUuid, userUuid);
                if (membership != null && Boolean.TRUE.equals(membership.get("member"))) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                // Invalid UUID format từ database - skip group này
                System.err.println("Invalid groupId format: " + groupId);
            } catch (Exception e) {
                // Log error nhưng tiếp tục check các groups khác
                System.err.println("Error checking membership for group " + groupId + ": " + e.getMessage());
            }
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findAccessibleDocumentIds(List<UUID> documentIds, String userId) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> allGroupIds = new HashSet<>();
        Map<String, List<UUID>> groupToDocuments = new HashMap<>();

        for (UUID docId : documentIds) {
            List<String> groupIds = groupDocumentRepository.findGroupIdsByDocumentId(docId);
            for (String groupId : groupIds) {
                allGroupIds.add(groupId);
                groupToDocuments.computeIfAbsent(groupId, k -> new ArrayList<>()).add(docId);
            }
        }

        if (allGroupIds.isEmpty()) {
            return Collections.emptyList();
        }

        UUID userUuid = UUID.fromString(userId);
        Set<String> accessibleGroupIds = new HashSet<>();

        for (String groupId : allGroupIds) {
            try {
                UUID groupUuid = UUID.fromString(groupId);
                Map<String, Object> membership = groupRest.checkMembership(groupUuid, userUuid);
                if (membership != null && Boolean.TRUE.equals(membership.get("member"))) {
                    accessibleGroupIds.add(groupId);
                }
            } catch (IllegalArgumentException e) {
                // Invalid UUID format từ database - skip group này
                System.err.println("Invalid groupId format: " + groupId);
            } catch (Exception e) {
                // Log error nhưng tiếp tục
                System.err.println("Error checking membership for group " + groupId + ": " + e.getMessage());
            }
        }

        // Lấy document IDs từ các groups mà user có quyền truy cập
        Set<UUID> accessibleDocumentIds = new HashSet<>();
        for (String groupId : accessibleGroupIds) {
            List<UUID> docIds = groupToDocuments.get(groupId);
            if (docIds != null) {
                accessibleDocumentIds.addAll(docIds);
            }
        }

        // Filter: chỉ giữ lại những documentIds có trong danh sách input
        return documentIds.stream()
                .filter(accessibleDocumentIds::contains)
                .collect(Collectors.toList());
    }

    /**
     * Mã hóa CEK cho tất cả members trong group
     * Được gọi khi thêm document vào group (Eager encryption)
     */
    private void encryptCEKForAllGroupMembers(UUID documentId, UUID groupId, HttpServletRequest httpRequest) throws Exception {
        // Lấy danh sách members trong group
        List<Map<String, Object>> members = groupRest.getGroupMembers(groupId);
        if (members == null || members.isEmpty()) {
            return; // Không có members, không cần mã hóa
        }

        // Lấy tất cả versions của document
        List<Object[]> versionRows = documentVersionRepository.getDocumentVersions(
                documentId.toString(), null, 1000, 0);
        if (versionRows.isEmpty()) {
            return; // Không có versions
        }

        List<UUID> versionIds = versionRows.stream()
                .map(row -> (UUID) row[0])
                .collect(Collectors.toList());

        // Lấy wrappedCEKMaster cho tất cả versions
        List<Object[]> versionCEKRows = documentVersionRepository.findWrappedCEKMasterByIds(versionIds);
        if (versionCEKRows.isEmpty()) {
            return;
        }

        // Tạo VersionCEK list
        List<VersionCEK> versionCEKList = versionCEKRows.stream()
                .map(row -> {
                    UUID versionId = (UUID) row[0];
                    Object wrappedCEKMasterObj = row[1];
                    String wrappedCEKMaster = materializeLob(wrappedCEKMasterObj, versionId);
                    return new VersionCEK(versionId, wrappedCEKMaster);
                })
                .collect(Collectors.toList());

        // Mã hóa CEK cho từng member với từng version (parallel processing)
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map<String, Object> member : members) {
            String memberUserId = (String) member.get("userId");
            if (memberUserId == null) {
                continue;
            }

            for (VersionCEK versionCEK : versionCEKList) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        createDocumentKeyForMember(versionCEK, memberUserId, httpRequest);
                    } catch (Exception e) {
                        // Log error nhưng không throw để không block các members khác
                        System.err.println("Failed to create DocumentKey for member " + memberUserId +
                                " version " + versionCEK.getVersionId() + ": " + e.getMessage());
                    }
                }, encryptionExecutor);
                futures.add(future);
            }
        }

        // Đợi tất cả hoàn thành
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Tạo DocumentKey cho một member với một version
     * Sử dụng DocumentKeyService.createAndSaveKey để tái sử dụng logic đã có
     */
    private void createDocumentKeyForMember(VersionCEK versionCEK, String memberUserId, HttpServletRequest httpRequest) throws Exception {
        // Check xem đã có DocumentKey chưa
        Optional<DocumentKey> existingKey = documentKeyRepository
                .findByDocumentVersionIdAndRecipientId(versionCEK.getVersionId(), memberUserId);

        if (existingKey.isPresent()) {
            return; // Đã có rồi, không cần tạo lại
        }

        String wrappedCEKMaster = versionCEK.getWrappedCEKMaster();
        if (wrappedCEKMaster == null || !wrappedCEKMaster.startsWith("vault:")) {
            throw new RuntimeException("Invalid wrappedCEKMaster for version: " + versionCEK.getVersionId());
        }

        // Tạo CreateDocumentKeyRequest
        CreateDocumentKeyRequest request = CreateDocumentKeyRequest.builder()
                .documentVersionId(versionCEK.getVersionId())
                .recipientId(memberUserId)
                .wrappedByVault(wrappedCEKMaster.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .algorithm("OPENPGP_AES256")
                .build();

        // Sử dụng DocumentKeyService để tạo DocumentKey
        try {
            documentKeyService.createAndSaveKey(request, httpRequest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create DocumentKey for member " + memberUserId +
                    " version " + versionCEK.getVersionId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Materialize LOB (Clob) thành String
     */
    private String materializeLob(Object wrappedCEKMasterObj, UUID versionId) {
        if (wrappedCEKMasterObj instanceof String) {
            return (String) wrappedCEKMasterObj;
        } else if (wrappedCEKMasterObj instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) wrappedCEKMasterObj;
                long length = clob.length();
                if (length > Integer.MAX_VALUE) {
                    throw new RuntimeException("wrappedCEKMaster too large for version: " + versionId);
                }
                return clob.getSubString(1, (int) length);
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Failed to read wrappedCEKMaster for version: " + versionId, e);
            }
        } else {
            return wrappedCEKMasterObj.toString();
        }
    }

    @Override
    @Transactional
    public void ensureDocumentKeyExists(UUID documentId, UUID versionId, String userId, HttpServletRequest httpRequest) throws Exception {
        // Check xem đã có DocumentKey chưa
        if (documentKeyRepository.existsByDocumentVersionIdAndRecipientId(versionId, userId)) {
            return; // Đã có rồi, không cần tạo
        }

        // Check xem user có quyền truy cập qua group không
        if (!checkDocumentGroupAccess(documentId, userId)) {
            throw new AppException(AuthError.FORBIDDEN, "User does not have access to this document via group");
        }

        // Lấy version và wrappedCEKMaster
        DocumentVersion version = documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new AppException(NotExistError.VERSION_NOT_FOUND));

        if (version.getWrappedCEKMaster() == null) {
            throw new AppException(NotExistError.VERSION_NOT_WRAPPED_CEK_MASTER);
        }

        String wrappedCEKMaster = materializeLob(version.getWrappedCEKMaster(), versionId);
        VersionCEK versionCEK = new VersionCEK(versionId, wrappedCEKMaster);

        // Tạo DocumentKey (lazy encryption)
        createDocumentKeyForMember(versionCEK, userId, httpRequest);
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

