package com.system_share_documents.AuditLogService.dto.response;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private String userId;
    private String affectedUsers;
    private String action;
    private String documentId;
    private String objectType;
    private String status;
    private String ip;
    private String userAgent;
    private Timestamp createdAt;
    private String metadata; // hoặc Map<String, Object> nếu bạn parse
}