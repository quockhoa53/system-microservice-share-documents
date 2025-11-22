package com.system_share_documents.AuthAccessControlService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckAccessRequest {
    private String documentId;
    private String userId;
}
