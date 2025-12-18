package com.system_share_documents.DocumentService.entity;

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
        name = "group_documents",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"document_id", "group_id"})
        }
)
public class GroupDocument {

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId; // ID của document được chia sẻ với group

    @Column(name = "group_id", nullable = false, length = 36)
    private String groupId; // ID của group (từ UserService, dạng UUID string)

    @Column(name = "added_by", nullable = false, length = 36)
    private String addedBy; // ID của user thêm document vào group

    @Column(name = "access_role", length = 32)
    private String accessRole; // Quyền truy cập mặc định cho group: "viewer", "editor", "admin"

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // Thời điểm thêm document vào group

    @Column(name = "deleted_at")
    private Timestamp deletedAt; // Thời điểm xóa (soft delete), NULL = chưa xóa

    @Column(name = "updated_at")
    private Timestamp updatedAt; // Thời điểm cập nhật gần nhất
}


