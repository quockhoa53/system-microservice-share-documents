package com.system_share_documents.DocumentService.dto.request;

import com.system_share_documents.DocumentService.enums.AccessRoleGroupDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddDocumentToGroupRequest {
    private String documentId;
    private String groupId;
    private AccessRoleGroupDocument accessRole;
}
