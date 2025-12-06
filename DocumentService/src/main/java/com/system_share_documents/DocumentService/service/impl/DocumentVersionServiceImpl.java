package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.dto.request.GetListDocumentVersionRequest;
import com.system_share_documents.DocumentService.dto.response.DocumentVersionResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.DocumentVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentVersionServiceImpl implements DocumentVersionService {

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Transactional
    public void createDocumentVersion(Document doc, int versionNumber, String objectKey, long sizeBytes) {
        try {
            DocumentVersion version = DocumentVersion.builder()
                    .document(doc)
                    .versionNumber(versionNumber)
                    .status(VersionStatus.UPLOADING)
                    .storageObjectKey(objectKey)
                    .sizeBytes(sizeBytes)
                    .watermarked(false)
                    .createdAt(Timestamp.from(Instant.now()))
                    .build();
            documentVersionRepository.save(version);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<DocumentVersionResponse> getDocumentVersion(GetListDocumentVersionRequest request, int page, int size) {
        int limit = size;
        int offset = (page - 1) * size;

        String[] listStatus = null;
        if(request.getStatus() != null && !request.getStatus().isEmpty()) {
            listStatus = request.getStatus().toArray(new String[0]);
        }

        List<Object[]> results = documentVersionRepository.getDocumentVersions(request.getDocumentId(), listStatus, limit, offset);

        return results.stream().map(row -> new DocumentVersionResponse(
                (UUID) row[0],
                (Integer) row[1],
                VersionStatus.valueOf((String) row[2]),
                (String) row[3],
                (String) row[4],
                (Long) row[5],
                (Long) row[6],
                (Timestamp) row[7],
                (Timestamp) row[8]
        )).toList();

    }
}
