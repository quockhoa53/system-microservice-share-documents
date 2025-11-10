package com.system_share_documents.AppCommonService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateDocumentKeyRequest {
    private UUID documentVersionId;
    private String recipientId;
    private byte[] rawCek;
    private String algorithm;
}
