package com.system_share_documents.DocumentService.dto.request;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewDocumentRequest {
    private UUID documentId;
    private UUID versionId;
    private Boolean isGroup;
}
