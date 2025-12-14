package com.system_share_documents.DocumentService.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
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

    public DocumentResponse mapToDocumentResponse(Map<String, Object> sourceMap) {
        try {
            DocumentResponse response = new DocumentResponse();

            // Map id
            if (sourceMap.get("id") != null) {
                response.setDocumentId(sourceMap.get("id").toString());
            }

            // Map originalFilename
            if (sourceMap.get("original_filename") != null) {
                response.setOriginalFilename(sourceMap.get("original_filename").toString());
            }

            // Map contentType
            if (sourceMap.get("content_type") != null) {
                response.setContentType(sourceMap.get("content_type").toString());
            }

            // Map sizeBytes
            if (sourceMap.get("size_bytes") != null) {
                if (sourceMap.get("size_bytes") instanceof Number) {
                    response.setSizeBytes(((Number) sourceMap.get("size_bytes")).longValue());
                } else {
                    response.setSizeBytes(Long.parseLong(sourceMap.get("size_bytes").toString()));
                }
            }

            // Map checksum
            if (sourceMap.get("checksum") != null) {
                response.setChecksum(sourceMap.get("checksum").toString());
            }

            // Map storageClass
            if (sourceMap.get("storage_class") != null) {
                response.setStorageClass(sourceMap.get("storage_class").toString());
            }

            // Map ownerId
            if (sourceMap.get("owner_id") != null) {
                response.setOwnerId(sourceMap.get("owner_id").toString());
            }

            // Map metadata
            if (sourceMap.get("metadata") != null) {
                Object metadata = sourceMap.get("metadata");
                if (metadata instanceof Map) {
                    response.setMetadata(metadata);
                }
            }

            // Map createdAt
            if (sourceMap.get("created_at") != null) {
                Object createdAt = sourceMap.get("created_at");
                if (createdAt instanceof Number) {
                    long epochMillis = ((Number) createdAt).longValue();
                    response.setCreatedAt(epochMillis);
                } else {
                    try {
                        response.setCreatedAt(Long.parseLong(createdAt.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse created_at: {}", createdAt);
                    }
                }
            }

            // Map updatedAt
            if (sourceMap.get("updated_at") != null) {
                Object updatedAt = sourceMap.get("updated_at");
                if (updatedAt instanceof Number) {
                    long epochMillis = ((Number) updatedAt).longValue();
                    response.setUpdatedAt(epochMillis);
                } else {
                    try {
                        response.setUpdatedAt(Long.parseLong(updatedAt.toString()));
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse updated_at: {}", updatedAt);
                    }
                }
            }

            return response;
        } catch (Exception e) {
            log.error("Error mapping Elasticsearch document to DocumentResponse: {}", e.getMessage(), e);
            return null;
        }
    }
}
