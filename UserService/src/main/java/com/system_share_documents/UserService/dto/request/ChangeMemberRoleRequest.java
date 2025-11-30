package com.system_share_documents.UserService.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChangeMemberRoleRequest {
    @NotNull
    private String role; // "admin" | "member"
}
