package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.PreviewDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.PreviewDocumentResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface PreviewDocumentService {
    PreviewDocumentResponse previewDocument(PreviewDocumentRequest request, String userId, HttpServletRequest httpRequest) throws Exception;
}
