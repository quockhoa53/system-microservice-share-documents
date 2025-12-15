package com.system_share_documents.UserService.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Entity lưu encrypted backup của private keys
 * Server KHÔNG THỂ đọc được nội dung - chỉ lưu encrypted blob
 * User cần recovery passphrase để decrypt
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_key_backups")
public class UserKeyBackup {

    @Id
    @GeneratedValue
    UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user; // Mỗi user chỉ có 1 backup

    @Lob
    @Column(name = "encrypted_backup", nullable = false, columnDefinition = "text")
    String encryptedBackup; // Encrypted blob - server KHÔNG THỂ đọc được

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    Timestamp createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    Timestamp updatedAt;
}
