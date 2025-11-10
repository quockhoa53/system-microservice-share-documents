package com.system_share_documents.DocumentService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitUploadResponse {
    private UUID documentId;
    private Integer versionNumber;
    private UploadUrlsResponse uploadUrls;
    private boolean tempChecksumRequired;
}
