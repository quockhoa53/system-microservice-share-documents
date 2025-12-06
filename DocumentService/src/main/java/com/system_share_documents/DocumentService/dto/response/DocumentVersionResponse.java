package com.system_share_documents.DocumentService.dto.response;

import com.system_share_documents.DocumentService.enums.VersionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionResponse {
    private UUID versionId;
    private Integer versionNumber;
    private VersionStatus status;
    private String name;
    private String description;
    private Long sizeBytes;
    private Long downloadCount;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
