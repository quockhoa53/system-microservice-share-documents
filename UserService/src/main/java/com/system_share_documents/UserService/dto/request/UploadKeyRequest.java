package com.system_share_documents.UserService.dto.request;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadKeyRequest {
    private UUID userId;
    private List<KeyItem> keys;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeyItem {
        private String keyType;          // "openpgp-ed25519" | "openpgp-cv25519"
        private String publicKeyArmored; // ASCII-armored
        private String fingerprint;      // hex
        private Boolean primary;
    }
}