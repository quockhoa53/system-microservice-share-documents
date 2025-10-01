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
@Table(name = "document_keys")
public class DocumentKey {

    @Id
    @GeneratedValue
    private UUID id;
    /*
     * Khóa chính cho bảng document_keys.
     * Mỗi bản ghi tương ứng với 1 CEK (Content Encryption Key) được wrap.
     */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersion documentVersion;
    /*
     * Quan hệ N-1 tới DocumentVersion.
     * CEK này dùng để mã hóa nội dung của version cụ thể.
     * → Không cần lưu version_number riêng, tránh trùng lặp.
     */

    @Column(name = "recipient_id", length = 64, nullable = false)
    private String recipientId;

    @Lob
    @Column(name = "wrapped_cek", nullable = false)
    private byte[] wrappedCek;
    /*
     * CEK đã được KMS/HSM wrap bằng master key.
     * Đây KHÔNG phải plaintext CEK.
     * Chỉ có thể unwrap qua KMS để lấy CEK gốc phục vụ giải mã file.
     */

    @Column(name = "algorithm", length = 128)
    private String algorithm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;
    /*
     * Thời điểm tạo CEK cho version này.
     * Dùng cho mục đích audit/tracking.
     */
}
