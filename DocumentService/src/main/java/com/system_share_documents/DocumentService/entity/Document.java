package com.system_share_documents.DocumentService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id; // định danh duy nhất cho tài liệu

    @Column(name = "owner_id", nullable = false, length = 36)
    private String ownerId; // người upload tài liệu (từ UserService)

    @Column(name = "original_filename", nullable = false, length = 1024)
    private String originalFilename; // tên file gốc do user upload

    @Column(name = "content_type", length = 256)
    private String contentType; // MIME type (vd: application/pdf, image/png)

    @Column(name = "size_bytes")
    private Long sizeBytes; // kích thước file (bytes)

    @Column(name = "checksum", length = 128)
    private String checksum; // hash SHA-256 để kiểm chứng toàn vẹn dữ liệu

    @Column(name = "storage_class", length = 32)
    private String storageClass = "standard"; // phân loại lưu trữ: standard, archive, etc.

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // thời điểm tạo tài liệu

    @Column(name = "updated_at", nullable = false)
    private Timestamp updatedAt; // thời điểm cập nhật metadata cuối cùng

    @Column(name = "deleted_at")
    private Timestamp deletedAt; // thời điểm xóa (soft delete), NULL = chưa xóa

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    // Quan hệ 1-N với DocumentVersion
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DocumentVersion> versions = new ArrayList<>();

    // Quan hệ 1-N với Signature
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Signature> signatures = new ArrayList<>();
}

