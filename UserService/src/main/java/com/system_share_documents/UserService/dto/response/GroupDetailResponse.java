package com.system_share_documents.UserService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupDetailResponse {
    private UUID id;
    private String name;
    private String description;
    private Short visibility;
    private UUID ownerId;
    private Timestamp createdAt;
    private Long memberCount;
    private String myRole; // "owner" | "admin" | "member" | null
}