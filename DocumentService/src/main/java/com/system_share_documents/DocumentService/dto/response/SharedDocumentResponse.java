package com.system_share_documents.DocumentService.dto.response;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedDocumentResponse {

    @JsonAlias("document_id")
    private String documentId;

    @JsonAlias("owner_id")
    private String ownerId;

    @JsonAlias("original_filename")
    private String originalFilename;

    @JsonAlias("content_type")
    private String contentType;

    @JsonAlias("size_bytes")
    private Long sizeBytes;

    @JsonAlias("storage_class")
    private String storageClass;

    private Object metadata;

    @JsonAlias("created_at")
    private Long createdAt;

    @JsonAlias("updated_at")
    private Long updatedAt;

    private List<SharedDocumentVersionResponse> versions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedDocumentVersionResponse {
        @JsonAlias("version_id")
        private String versionId;

        @JsonAlias("version_number")
        private Integer versionNumber;

        private String name;

        private String description;

        private String status;

        private Long downloadCount;

        @JsonAlias("created_at")
        private Long createdAt;

        @JsonAlias("storage_object_key")
        private String storageObjectKey;
    }
}
