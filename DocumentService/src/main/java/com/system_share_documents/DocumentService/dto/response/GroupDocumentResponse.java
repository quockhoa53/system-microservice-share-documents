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
public class GroupDocumentResponse {
    private UUID id;
    private UUID documentId;
    private String documentName;
    private String groupId;
    private String groupName;
    private String addedBy;
    private String accessRole;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
