package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.AddDocumentToGroupRequest;
import com.system_share_documents.DocumentService.dto.request.GetGroupDocumentsRequest;
import com.system_share_documents.DocumentService.dto.request.RemoveDocumentFromGroupRequest;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentDetailResponse;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentResponse;
import com.system_share_documents.DocumentService.service.GroupDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/group-documents")
public class GroupDocumentController {

    @Autowired
    private GroupDocumentService groupDocumentService;

    /**
     * Thêm document vào group
     * POST /api/group-documents/add
     */
    @PostMapping("/add")
    public ApiResponse<GroupDocumentResponse> addDocumentToGroup(
            @RequestBody AddDocumentToGroupRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) throws Exception {
        String userId = auth.getName();
        GroupDocumentResponse response = groupDocumentService.addDocumentToGroup(request, userId, httpRequest);
        return ApiResponse.success("OK", "Document added to group successfully", response);
    }

    /**
     * Xóa document khỏi group
     * POST /api/group-documents/remove
     */
    @PostMapping("/remove")
    public ApiResponse<Void> removeDocumentFromGroup(
            @RequestBody RemoveDocumentFromGroupRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) throws Exception {
        String userId = auth.getName();
        groupDocumentService.removeDocumentFromGroup(request, userId);
        return ApiResponse.success("OK", "Document removed from group successfully", null);
    }

    /**
     * Lấy danh sách documents trong group (có phân trang)
     * POST /api/group-documents/group/list
     */
    @PostMapping("/group/list")
    public ApiResponse<Page<GroupDocumentDetailResponse>> getGroupDocuments(
            @RequestBody GetGroupDocumentsRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) throws Exception {
        String userId = auth.getName();
        Page<GroupDocumentDetailResponse> response = groupDocumentService.getGroupDocuments(request, userId);
        return ApiResponse.success("OK", "Get group documents successfully", response);
    }

    /**
     * Lấy danh sách groups chứa document
     * GET /api/group-documents/document/{documentId}/groups
     */
    @GetMapping("/document/{documentId}/groups")
    public ApiResponse<List<GroupDocumentResponse>> getDocumentGroups(
            @PathVariable String documentId,
            Authentication auth,
            HttpServletRequest httpRequest
    ) throws Exception {
        String userId = auth.getName();
        UUID docId = UUID.fromString(documentId);
        List<GroupDocumentResponse> response = groupDocumentService.getDocumentGroups(docId, userId);
        return ApiResponse.success("OK", "Get document groups successfully", response);
    }
}
