package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;
import com.system_share_documents.DocumentService.service.UploadDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/upload")
public class UploadDocumentController {

    @Autowired
    private UploadDocumentService uploadDocumentService;

    @GetMapping("/init-upload")
    public ApiResponse<InitUploadResponse> initUpload(@RequestBody InitUploadRequest request, Authentication auth) throws Exception {
        String ownerId = auth.getName();
        InitUploadResponse response = uploadDocumentService.initUpload(request, ownerId);
        return ApiResponse.success("OK", "Init upload document success", response);
    }
}
