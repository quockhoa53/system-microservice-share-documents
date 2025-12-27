package com.system_share_documents.DocumentService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewDocumentResponse {
    private UUID documentId;
    private UUID versionId;
    private String preSignedUrl;
    private String contentType;
    private Long sizeBytes;
    private Timestamp expiresAt;
    private String wrappedCek;
}