package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.dto.request.DownLoadDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.DownLoadDocumentResponse;
import com.system_share_documents.DocumentService.service.DownLoadDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/download")
public class DownLoadDocumentController {

    @Autowired
    private DownLoadDocumentService downLoadDocumentService;

    @PostMapping("/get")
    public ApiResponse<DownLoadDocumentResponse> downloadController(@RequestBody DownLoadDocumentRequest request, Authentication auth, HttpServletRequest httpRequest) throws Exception {
        String ownerId = auth.getName();
        DownLoadDocumentResponse response = downLoadDocumentService.getDownLoadDocument(request, ownerId, httpRequest);
        return ApiResponse.success("OK", "Download document success", response);
    }
}
