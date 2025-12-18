package com.system_share_documents.DocumentService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private String documentId;
    private long sizeBytes;
    private String storageClass;
    private String ownerId;
    private String checksum;
    private String contentType;
    private String originalFilename;
    private Object metadata;
    private long createdAt;
    private long updatedAt;
    // Permission fields - chỉ được set khi có userId trong search
    private Boolean isOwner;
    private Boolean canDownload;
    private Boolean hasAccess;
}
