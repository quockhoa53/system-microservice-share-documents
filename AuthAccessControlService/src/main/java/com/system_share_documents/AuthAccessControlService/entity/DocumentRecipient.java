package com.system_share_documents.AuthAccessControlService.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "document_recipients",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"document_id", "recipient_user_id"})}
)
public class DocumentRecipient {

    @Id
    @GeneratedValue
    private UUID id; // định danh duy nhất cho mối quan hệ chia sẻ tài liệu

    @Column(name = "document_id", nullable = false)
    private String documentId; // tài liệu được chia sẻ

    @Column(name = "recipient_user_id", nullable = false)
    private String recipientUserId; // ID của user nhận tài liệu (từ User Service)

    @Column(name = "recipient_key_id")
    private String recipientKeyId; // ID của key được dùng để mã hóa CEK (từ User Service)

    @Column(name = "encrypted_cek", nullable = false)
    private byte[] encryptedCek; // CEK đã được mã hóa bằng public key của recipient

    @Column(name = "access_role", nullable = false, length = 32)
    private String accessRole = "reader"; // quyền: reader, editor...

    @Column(name = "expires_at")
    private Timestamp expiresAt; // ngày hết hạn quyền truy cập (có thể null)

    @Column(name = "can_download")
    private Boolean canDownload = true; // có cho phép download không

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // thời điểm được cấp quyền
}

