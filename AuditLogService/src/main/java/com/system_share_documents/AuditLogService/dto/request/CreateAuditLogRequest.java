package com.system_share_documents.AuditLogService.dto.request;


import lombok.Data;

import java.util.Map;

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

    // Trạng thái: ok, fail
    private String status;

    // IP & user agent (có thể được Document/User Service gửi sang)
    private String ip;
    private String userAgent;

    // Metadata bổ sung: lưu JSON để truy vết chi tiết (vd: { "groupId": "...", "keyId": "..." })
    private Map<String, Object> metadata;
}