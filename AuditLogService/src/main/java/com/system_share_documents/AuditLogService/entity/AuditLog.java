package com.system_share_documents.AuditLogService.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // ID tự tăng, định danh duy nhất log

    @Column(name = "user_id", length = 36)
    private String userId; // ID người thực hiện hành động (String UUID)

    @Column(name = "action", nullable = false, length = 64)
    private String action; // hành động: upload, download, decrypt, verify,...

    @Column(name = "document_id", length = 36)
    private String documentId; // ID tài liệu liên quan (nếu có)

    @Column(name = "object_type", length = 32)
    private String objectType; // loại object: document, group,...

    @Column(name = "status", length = 32)
    private String status; // trạng thái: ok, fail

    @Column(name = "ip", length = 64)
    private String ip; // IP thực hiện hành động

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent; // user-agent của client

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // thời điểm tạo log

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata; // thông tin bổ sung dạng JSON
}

