package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.PreviewDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.PreviewDocumentResponse;
import com.system_share_documents.DocumentService.service.PreviewDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/preview")
public class PreviewDocumentController {

    @Autowired
    private PreviewDocumentService previewDocumentService;

    /**
     * Preview một version cụ thể của document (GET endpoint).
     * Nếu versionId được chỉ định trong query param, sẽ preview version đó.
     * Nếu versionId không được chỉ định, sẽ preview version mới nhất (AVAILABLE).
     *
     * @param request ID của document và ID của version cần preview
     * @param auth thông tin authentication của user
     * @param httpRequest HTTP request để lấy thông tin client (IP, User-Agent)
     * @return PreviewDocumentResponse chứa presigned URL để xem trước document
     */
    @PostMapping("get")
    public ApiResponse<PreviewDocumentResponse> previewDocumentLatest(
            @RequestBody PreviewDocumentRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) throws Exception {
        String userId = auth.getName();
        PreviewDocumentResponse response = previewDocumentService.previewDocument(request, userId, httpRequest);
        return ApiResponse.success("OK", "Preview URL generated successfully", response);
    }
}
