package com.system_share_documents.AuthAccessControlService.entity;

import com.system_share_documents.AuthAccessControlService.enums.DocumentAccessRole;
import com.system_share_documents.AuthAccessControlService.enums.RecipientType;
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
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"document_id", "recipient_user_id", "recipient_type"}),
                @UniqueConstraint(columnNames = {"document_id", "recipient_group_id", "recipient_type"})
        }
)
public class DocumentRecipient {

    @Id
    @GeneratedValue
    private UUID id; // định danh duy nhất cho mối quan hệ chia sẻ tài liệu

    @Column(name = "document_id", nullable = false)
    private String documentId; // tài liệu được chia sẻ

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false, length = 10)
    private RecipientType recipientType = RecipientType.USER; // Loại recipient: USER hoặc GROUP

    @Column(name = "recipient_user_id")
    private String recipientUserId; // ID của user nhận tài liệu (từ User Service) - nullable khi recipientType=GROUP

    @Column(name = "recipient_group_id")
    private String recipientGroupId; // ID của group nhận tài liệu - nullable khi recipientType=USER

    @Enumerated(EnumType.STRING)
    @Column(name = "access_role", nullable = false, length = 10)
    private DocumentAccessRole accessRole = DocumentAccessRole.VIEWER;

    @Column(name = "expires_at")
    private Timestamp expiresAt; // ngày hết hạn quyền truy cập (có thể null)

    @Column(name = "can_download")
    private Boolean canDownload = true; // có cho phép download không

    @Column(name = "is_revoke")
    private Boolean isRevoke = false; // Đã thu hồi quyền hay chưa

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // thời điểm được cấp quyền

    @Column(name = "updated_at")
    private Timestamp updatedAt; // thời điểm cập nhật quyền
}

