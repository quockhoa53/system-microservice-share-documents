package com.system_share_documents.DocumentService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestAccessResponse {
    private String documentId;
    private String requesterUserId;
    private String ownerId;
    private String ownerEmail;
    private boolean emailSent;
    private String message;
}
