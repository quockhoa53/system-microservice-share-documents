package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.GetListDocumentVersionRequest;
import com.system_share_documents.DocumentService.dto.response.DocumentVersionResponse;
import com.system_share_documents.DocumentService.entity.Document;

import java.util.List;

public interface DocumentVersionService {
    void createDocumentVersion(Document doc, int versionNumber, String objectKey, long sizeBytes);
    List<DocumentVersionResponse> getDocumentVersion(GetListDocumentVersionRequest request, int page, int size);
}
