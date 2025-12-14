package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.DeleteVersionsRequest;
import com.system_share_documents.DocumentService.dto.request.GetListDocumentVersionRequest;
import com.system_share_documents.DocumentService.dto.response.DeleteVersionsResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentVersionResponse;
import com.system_share_documents.DocumentService.service.DocumentVersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents/version/")
public class DocumentVersionController {

    @Autowired
    private DocumentVersionService documentVersionService;

    @PostMapping("get/lists")
    public ApiResponse<List<DocumentVersionResponse>> getDocumentVersionController(@RequestBody GetListDocumentVersionRequest request,
                                                                                   @RequestParam(defaultValue = "1") int page,
                                                                                   @RequestParam(defaultValue = "20") int size,
                                                                                   HttpServletRequest httpRequest) throws Exception {
        List<DocumentVersionResponse> response = documentVersionService.getDocumentVersion(request, page, size);
        return ApiResponse.success("OK", "Get list document version of document successfully", response);
    }

    @DeleteMapping("delete")
    public ApiResponse<DeleteVersionsResponse> deleteVersionsController(@RequestBody DeleteVersionsRequest request, Authentication auth, HttpServletRequest httpRequest) throws Exception {
        String userId = auth.getName();
        DeleteVersionsResponse response = documentVersionService.deleteVersions(request, userId, httpRequest);
        return ApiResponse.success("OK", "Versions deleted successfully", response);
    }

}
