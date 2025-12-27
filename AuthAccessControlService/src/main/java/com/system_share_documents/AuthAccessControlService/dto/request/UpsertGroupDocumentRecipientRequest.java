package com.system_share_documents.AuthAccessControlService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertGroupDocumentRecipientRequest {
    private String documentId;  // ID của document
    private String groupId;     // ID của group
    private String accessRole;  // AccessRoleGroupDocument: VIEWER, SHARE, DOWNLOAD, ADMIN, REVOKE
}
