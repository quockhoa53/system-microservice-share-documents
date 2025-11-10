package com.system_share_documents.UserService.dto.response;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;
import java.util.UUID;

@Builder @Data
public class PublicKeyResponse {
    private UUID id;
    private String keyType;
    private String publicKeyArmored;
    private String fingerprint;
    private Boolean primary;
    private Timestamp createdAt;
    private Timestamp revokedAt;
}