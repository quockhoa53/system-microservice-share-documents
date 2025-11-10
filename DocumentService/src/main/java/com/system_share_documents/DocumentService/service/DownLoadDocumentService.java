package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.DownLoadDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.DownLoadDocumentResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface DownLoadDocumentService {
    DownLoadDocumentResponse getDownLoadDocument(DownLoadDocumentRequest request, String userId, HttpServletRequest httpRequest) throws Exception;
}
