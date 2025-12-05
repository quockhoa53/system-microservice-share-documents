package com.system_share_documents.UserService.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddMemberRequest {

    @NotNull
    private UUID userId;

    // "admin" hoặc "member"
    @NotNull
    private String role;
}