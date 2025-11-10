package com.system_share_documents.DocumentService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteUploadResponse {
    private UUID documentId;
    private UUID versionId;
    private String storageObjectKey;
    private String checksum;
    private long sizeBytes;
    private String status;
}
