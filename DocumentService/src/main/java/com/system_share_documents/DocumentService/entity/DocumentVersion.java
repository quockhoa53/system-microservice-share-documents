package com.system_share_documents.DocumentService.entity;

import com.system_share_documents.DocumentService.enums.VersionStatus;
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
@Table(name = "document_versions",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"document_id", "version_number"})})
public class DocumentVersion {

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id; // định danh duy nhất cho phiên bản tài liệu

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document; // tham chiếu tới tài liệu gốc

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber; // số phiên bản (1 = bản đầu tiên, 2,3,...)

    @Column(name="status", length=32)
    @Enumerated(EnumType.STRING)
    private VersionStatus status;

    @Column(name="iv", length=64)
    private String ivHex; // optional AES-GCM IV (hex/base64)

    @Column(name = "storage_object_key", nullable = false, columnDefinition = "text")
    private String storageObjectKey; // object key của file mã hóa phiên bản này

    @Column(name = "size_bytes")
    private Long sizeBytes; // kích thước file phiên bản (bytes)

    @Column(name = "checksum", length = 128)
    private String checksum; // hash SHA-256 để kiểm chứng phiên bản này

    @Column(name = "watermarked", nullable = false)
    private Boolean watermarked = false; // đã qua watermark hay chưa

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // thời điểm tạo phiên bản
}
