package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.*;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentDetailResponse;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface GroupDocumentService {
    /**
     * Thêm document vào group
     */
    GroupDocumentResponse addDocumentToGroup(AddDocumentToGroupRequest request, String userId, HttpServletRequest httpRequest);

    /**
     * Xóa document khỏi group (soft delete)
     */
    void removeDocumentFromGroup(RemoveDocumentFromGroupRequest request, String userId);

    /**
     * Lấy danh sách documents trong group (có phân trang)
     */
    Page<GroupDocumentDetailResponse> getGroupDocuments(GetGroupDocumentsRequest request, String userId);

    /**
     * Lấy danh sách groups chứa document
     */
    List<GroupDocumentResponse> getDocumentGroups(UUID documentId, String userId);

    /**
     * Kiểm tra user có quyền truy cập document qua group membership không
     * (Internal method, dùng cho access control)
     */
    boolean checkDocumentGroupAccess(UUID documentId, String userId);

    /**
     * Batch check: kiểm tra nhiều documents cùng lúc
     */
    List<UUID> findAccessibleDocumentIds(List<UUID> documentIds, String userId);

    /**
     * Đảm bảo DocumentKey tồn tại cho user (lazy encryption)
     * Được gọi khi user truy cập document nhưng chưa có DocumentKey
     */
    void ensureDocumentKeyExists(UUID documentId, UUID versionId, String userId, HttpServletRequest httpRequest) throws Exception;

    GroupDocumentResponse updateDocumentAccessRoleInternal(UpdateDocumentAccessRoleRequest request);

    void dissolveGroup(DissolveGroupRequest request, String userId, HttpServletRequest httpRequest) throws Exception;
}
