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
public class DownLoadDocumentResponse {
    private UUID documentId;
    private UUID versionId;
    private String encryptedObjectKey;
    private String contentType;
    private String checksum;
    private String wrappedCek;
    private String preSignedUrl;
    private String algorithm;
    private Timestamp expiresAt;
}
