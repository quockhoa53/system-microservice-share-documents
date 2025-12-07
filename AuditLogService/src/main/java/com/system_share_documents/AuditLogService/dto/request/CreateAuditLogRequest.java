package com.system_share_documents.AuditLogService.dto.request;


import lombok.Data;

@Data
public class CreateAuditLogRequest {

    private String userId;

    private String affectedUsers;

    // Hành động: upload, download, decrypt, verify, share, revoke, ...
    private String action;

    // ID tài liệu (nếu có)
    private String documentId;

    // Loại đối tượng: document, group, key, user, ...
    private String objectType;

    // Loại log
    private String typeLog;

    // Trạng thái: ok, fail
    private String status;

    private String errorReason;

    // IP & user agent (có thể được Document/User Service gửi sang)
    private String ip;
    private String userAgent;
    private String request;
    // Metadata bổ sung: lưu JSON để truy vết chi tiết (vd: { "groupId": "...", "keyId": "..." })
    // Lưu dưới dạng JSON string để tương thích với AuditLogEvent từ Kafka
    private String metadata;

}