package com.system_share_documents.DocumentService.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import org.springframework.stereotype.Component;

@Component
public class MapperUtils {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentResponse mapDocumentCacheToResponse(DocumentCacheResponse c) throws JsonProcessingException {
        Object metadataObj = null;
        if (c.getMetadata() != null) {
            metadataObj = objectMapper.readValue(c.getMetadata(), Object.class);
        }
        return new DocumentResponse(
                c.getId(),
                c.getSize_bytes(),
                c.getStorage_class(),
                c.getOwner_id(),
                c.getChecksum(),
                c.getContent_type(),
                c.getOriginal_filename(),
                metadataObj,
                c.getCreated_at(),
                c.getUpdated_at()
        );
    }

    public DocumentResponse mapDocumentEntityToResponse(Document e) throws JsonProcessingException {
        Object metadataObj = e.getMetadata();
        return new DocumentResponse(
                e.getId().toString(),
                e.getSizeBytes(),
                e.getStorageClass(),
                e.getOwnerId(),
                e.getChecksum(),
                e.getContentType(),
                e.getOriginalFilename(),
                metadataObj,
                e.getCreatedAt().getTime(),
                e.getUpdatedAt().getTime()
        );
    }
}
