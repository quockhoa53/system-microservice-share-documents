package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;

public interface DocumentVersionService {
    DocumentVersion createDocumentVersion(Document doc, String objectKey, long sizeBytes);
}
