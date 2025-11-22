package com.system_share_documents.AuthAccessControlService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAccessResponse {
    private String documentId;
    private String recipientUserId;
    private String accessRole;
    private Boolean canDownload;
    private Timestamp expiresAt;
    private Timestamp grantedAt;
    private String message;
}
