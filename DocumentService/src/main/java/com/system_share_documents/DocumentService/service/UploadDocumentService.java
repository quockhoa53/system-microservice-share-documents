package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;

public interface UploadDocumentService {
    InitUploadResponse initUpload(InitUploadRequest request, String ownerId) throws Exception;
}
