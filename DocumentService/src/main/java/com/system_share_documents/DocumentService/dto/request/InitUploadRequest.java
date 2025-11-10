package com.system_share_documents.DocumentService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitUploadRequest {
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    private Map<String, Object> metadata;
    private List<String> recipients;
    private String storageClass;
}
