package com.system_share_documents.AuthAccessControlService.controller;

import com.system_share_documents.AuthAccessControlService.dto.ApiResponse;
import com.system_share_documents.AuthAccessControlService.dto.request.CheckAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GrantAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GetListRecipientsRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.RevokeAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.response.DocumentAccessResponse;
import com.system_share_documents.AuthAccessControlService.service.DocumentAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.simpleframework.xml.core.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/document-access")
public class DocumentAccessController {

    @Autowired
    private DocumentAccessService documentAccessService;

    @PostMapping("/grant")
    public ApiResponse<List<DocumentAccessResponse>> grantAccessDocumentController(@RequestBody GrantAccessRequest request, HttpServletRequest httpRequest) throws Exception {
        List<DocumentAccessResponse> response = documentAccessService.grantAccessDocument(request, httpRequest);
        return ApiResponse.success("OK", "Document access granted successfully", response);
    }

    @PostMapping("/revoke")
    public ApiResponse<List<DocumentAccessResponse>> revokeGrantAccessDocumentController(@RequestBody RevokeAccessRequest request, HttpServletRequest httpRequest) throws Exception {
        List<DocumentAccessResponse> response = documentAccessService.revokeGrantAccessDocument(request, httpRequest);
        return ApiResponse.success("OK", "Revoke grant access successfully", response);
    }

    @PostMapping("/list-recipients")
    public ApiResponse<List<DocumentAccessResponse>> listRecipientsDocumentController(@Validate @RequestBody GetListRecipientsRequest request, HttpServletRequest httpRequest) throws Exception {
        List<DocumentAccessResponse> response = documentAccessService.listRecipientsDocument(request, httpRequest);
        return ApiResponse.success("OK", "Get list recipients successfully", response);
    }

    @PostMapping("/check-access")
    public ApiResponse<DocumentAccessResponse> checkAccessDocumentController(@RequestBody CheckAccessRequest request, HttpServletRequest httpRequest) throws Exception {
        DocumentAccessResponse response = documentAccessService.checkAccessDocument(request, httpRequest);
        return ApiResponse.success("OK", "Check access successfully", response);
    }
}
