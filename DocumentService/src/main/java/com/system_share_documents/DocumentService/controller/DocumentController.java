package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.DeleteDocumentRequest;
import com.system_share_documents.DocumentService.dto.request.GetListDocumentRequest;
import com.system_share_documents.DocumentService.dto.request.RequestAccessRequest;
import com.system_share_documents.DocumentService.dto.request.SearchDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.DeleteDocumentResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.dto.response.RequestAccessResponse;
import com.system_share_documents.DocumentService.dto.response.SharedDocumentResponse;
import com.system_share_documents.DocumentService.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/")
public class DocumentController {

    @Autowired
    private DocumentService documentService;

    @PostMapping("get/lists")
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


    @PostMapping("search")
    public ApiResponse<List<DocumentResponse>> searchController(
            @RequestBody SearchDocumentRequest request,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit,
            Authentication auth
    ) throws Exception {
        String userId = auth != null ? auth.getName() : null;
        String keyword = request.getKeyword() != null ? request.getKeyword() : "";
        String userIdForFilter = null;
        if (request.getIsUser() != null && request.getIsUser() && userId != null) {
            userIdForFilter = userId;
        }
        List<DocumentResponse> response = documentService.searchDocuments(keyword, limit, userIdForFilter, userId);
        return ApiResponse.success("OK", "Search documents successfully", response);
    }

    @PostMapping("shared/get/lists")
    public ApiResponse<Page<SharedDocumentResponse>> getSharedDocuments(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size,
                                                                  Authentication auth,
                                                                  HttpServletRequest httpRequest) throws Exception {
        String userId = auth.getName();
        Page<SharedDocumentResponse> response = documentService.getSharedDocuments(userId, page, size);
        return ApiResponse.success("OK", "Get list shared documents successfully", response);
    }

    @DeleteMapping("delete")
    public ApiResponse<DeleteDocumentResponse> deleteDocument(@RequestBody DeleteDocumentRequest request, Authentication auth, HttpServletRequest httpRequest
    ) throws Exception {
        String userId = auth.getName();
        DeleteDocumentResponse response = documentService.deleteDocument(request, userId, httpRequest);
        return ApiResponse.success("OK", "Document deleted successfully", response);
    }

    @PostMapping("request-access")
    public ApiResponse<RequestAccessResponse> requestAccess(
            @RequestBody RequestAccessRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) throws Exception {
        String userId = auth.getName();
        RequestAccessResponse response = documentService.requestAccess(request, userId, httpRequest);
        return ApiResponse.success("OK", "Access request sent successfully", response);
    }
}
