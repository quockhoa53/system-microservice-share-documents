package com.system_share_documents.UserService.dto.request;

import lombok.Data;

@Data
public class ChangeMemberRoleRequest {
    private String role; // "admin" | "member"
}
