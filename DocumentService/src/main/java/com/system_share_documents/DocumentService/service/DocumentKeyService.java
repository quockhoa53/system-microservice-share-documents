package com.system_share_documents.DocumentService.service;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface DocumentKeyService {
    void createAndSaveKey(CreateDocumentKeyRequest request, HttpServletRequest httpRequest) throws Exception;
    byte[] getDocumentKeyForUser(UUID versionId, String recipientId);
}
