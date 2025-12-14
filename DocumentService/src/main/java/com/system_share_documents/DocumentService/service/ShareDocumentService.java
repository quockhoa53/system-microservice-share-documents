package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.ShareDocumentRequest;
import jakarta.servlet.http.HttpServletRequest;

public interface ShareDocumentService {
    void shareDocument(ShareDocumentRequest request, HttpServletRequest httpRequest) throws Exception;
}
