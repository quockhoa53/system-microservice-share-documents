package com.system_share_documents.UserService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKeyResponse_test {
    private String publicKey;
    private String keyType;
    private Boolean isPrimary;
    private String keyFingerprint;
}
