package com.system_share_documents.DocumentService.controller;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import com.system_share_documents.DocumentService.dto.ApiResponse;
import com.system_share_documents.DocumentService.service.DocumentKeyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/key")
public class DocumentKeyController {

    @Autowired
    private DocumentKeyService documentKeyService;

    @PostMapping("/create")
    public ApiResponse<Void> createDocumentKey(@RequestBody CreateDocumentKeyRequest request, HttpServletRequest httpRequest) throws Exception {
        documentKeyService.createAndSaveKey(request, httpRequest);
        return ApiResponse.success("OK", "Create document key success", null);
    }
}
