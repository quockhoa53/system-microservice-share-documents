package com.system_share_documents.UserService.dto.request;

import lombok.Data;

import java.util.UUID;

@Data
public class AddMemberRequest {

    private UUID userId;

    // "admin" hoặc "member"
    private String role;
}