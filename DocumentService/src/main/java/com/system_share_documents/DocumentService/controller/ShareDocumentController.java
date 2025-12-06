package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.CompleteUploadRequest;
import com.system_share_documents.DocumentService.dto.request.ShareDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.CompleteUploadResponse;
import com.system_share_documents.DocumentService.service.ShareDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/")
public class ShareDocumentController {

    @Autowired
    private ShareDocumentService shareDocumentService;

    @PostMapping("/share")
    public ApiResponse<String> shareDocumentController(@RequestBody ShareDocumentRequest request, HttpServletRequest httpRequest) throws Exception {
        shareDocumentService.shareDocument(request, httpRequest);
        return ApiResponse.success("OK", "Complete upload document success", null);
    }
}
