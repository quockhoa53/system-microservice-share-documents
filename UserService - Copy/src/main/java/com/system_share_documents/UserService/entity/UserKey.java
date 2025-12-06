package com.system_share_documents.UserService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_keys")
public class UserKey {

    @Id
    @GeneratedValue
    UUID id; // định danh duy nhất cho mỗi key

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user; // user sở hữu cặp key này

    @Column(name = "key_type", nullable = false, length = 32)
    String keyType; // loại key, ví dụ: "openpgp", "x25519"

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    String publicKey; // public key được lưu (dạng armored string)

    @Column(name = "private_key_encrypted")
    byte[] privateKeyEncrypted; // private key (nếu lưu server-side, phải được mã hóa bằng KMS/HSM);
    // có thể NULL nếu private key chỉ lưu client-side

    @Column(name = "key_fingerprint", length = 128)
    String keyFingerprint; // fingerprint để xác định nhanh key (ngắn gọn hơn full key)

    @Column(name = "is_primary", nullable = false)
    Boolean isPrimary = false; // key chính của user (dùng mặc định để ký/mã hóa)

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private Timestamp createdAt;// thời điểm tạo key

    @Column(name = "revoked_at")
    Timestamp revokedAt; // nếu key bị thu hồi, lưu thời điểm revoke
}
