package com.system_share_documents.UserService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserDetailResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private Short status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String keycloakUserId; // ID từ Keycloak (nếu cần)
    
    // Keys information
    private List<UserKeyInfo> keys;
    private long totalKeys;
    private long activeKeys;
    
    // Groups information
    private List<GroupInfo> groups;
    private long totalGroups;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserKeyInfo {
        private UUID keyId;
        private String keyType;
        private boolean isPrimary;
        private Timestamp createdAt;
        private Timestamp revokedAt;
        private String fingerprint;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupInfo {
        private UUID groupId;
        private String groupName;
        private String role; // owner, admin, member
        private Timestamp joinedAt;
    }
}
