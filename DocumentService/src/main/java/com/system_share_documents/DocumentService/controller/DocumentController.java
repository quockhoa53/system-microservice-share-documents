package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.GetListDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("/get/lists")
    public ApiResponse<Page<DocumentResponse>> getListDocumentController(@RequestBody(required = false) GetListDocumentRequest request, Authentication auth, HttpServletRequest httpRequest) throws Exception {
        String userId = auth.getName();
        int page = 0;
        int size = 20;
        if (request != null) {
            if (request.getUserId() != null) {
                userId = request.getUserId();
            }
            if (request.getPage() != null) {
                page = request.getPage();
            }
            if (request.getSize() != null) {
                size = request.getSize();
            }
        }
        Page<DocumentResponse> response = documentService.getListDocumentOfUser(userId, page, size);
        return ApiResponse.success("OK", "Get list the user's document successfully", response);
    }

}
