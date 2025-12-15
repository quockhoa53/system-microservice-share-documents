package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.AddDocumentToGroupRequest;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentResponse;
import com.system_share_documents.DocumentService.service.GroupDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public ApiResponse<GroupDocumentResponse> addDocumentToGroup(@RequestBody AddDocumentToGroupRequest request, Authentication auth, HttpServletRequest httpRequest) throws Exception {
        String userId = auth.getName();
        GroupDocumentResponse response = groupDocumentService.addDocumentToGroup(request, userId);
        return ApiResponse.success("OK", "Document added to group successfully", response);
    }
}
