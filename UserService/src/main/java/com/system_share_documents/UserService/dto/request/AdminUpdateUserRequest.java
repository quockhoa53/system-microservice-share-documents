package com.system_share_documents.UserService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUpdateUserRequest {
    private String fullName;
    private Short status; // 1 = active, 0 = disabled
    private String role; // admin, user, viewer - để sync với Keycloak
}
