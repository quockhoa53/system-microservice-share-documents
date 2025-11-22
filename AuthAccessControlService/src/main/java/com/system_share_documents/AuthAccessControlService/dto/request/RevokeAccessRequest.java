package com.system_share_documents.AuthAccessControlService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevokeAccessRequest {
    private String documentId;
    private List<String> recipientUserIds;
    private String type;
}
