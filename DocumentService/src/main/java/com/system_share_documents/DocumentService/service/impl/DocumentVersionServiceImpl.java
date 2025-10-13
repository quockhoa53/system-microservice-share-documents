package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.DocumentVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class DocumentVersionServiceImpl implements DocumentVersionService {

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Transactional
    public DocumentVersion createDocumentVersion(Document doc, String objectKey, long sizeBytes) {
        DocumentVersion version = DocumentVersion.builder()
                .document(doc)
                .versionNumber(1)
                .status(VersionStatus.UPLOADING)
                .storageObjectKey(objectKey)
                .sizeBytes(sizeBytes)
                .watermarked(false)
                .createdAt(Timestamp.from(Instant.now()))
                .build();
        documentVersionRepository.saveAndFlush(version);
        return version;
    }
}
