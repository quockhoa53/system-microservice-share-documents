package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.entity.DocumentVersion;

public interface DocumentKeyService {
    void createAndSaveKey(String recipient, DocumentVersion version, byte[] cekBytes) throws Exception;
}
