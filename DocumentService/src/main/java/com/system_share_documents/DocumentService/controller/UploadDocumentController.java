package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.CompleteUploadRequest;
import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.CompleteUploadResponse;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;
import com.system_share_documents.DocumentService.service.UploadDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/upload")
public class UploadDocumentController {

    @Autowired
    private UploadDocumentService uploadDocumentService;

    @PostMapping("/init-upload")
    public ApiResponse<InitUploadResponse> initUploadController(@RequestBody InitUploadRequest request, Authentication auth, HttpServletRequest httpRequest) throws Exception {
        String ownerId = auth.getName();
        InitUploadResponse response = uploadDocumentService.initUpload(request, ownerId, httpRequest);
        return ApiResponse.success("OK", "Init upload document success", response);
    }

    @PostMapping("/complete-upload")
    public ApiResponse<CompleteUploadResponse> completeUploadController(@RequestBody CompleteUploadRequest request, HttpServletRequest httpRequest) throws Exception {
        CompleteUploadResponse response = uploadDocumentService.completeUpload(request, httpRequest);
        return ApiResponse.success("OK", "Complete upload document success", response);
    }
}
