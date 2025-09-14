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
@Table(name = "signatures")
public class Signature {

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id; // định danh duy nhất cho chữ ký số

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document; // tài liệu được ký số

    @Column(name = "signer_user_id", length = 36)
    private String signerUserId; // ID người ký tài liệu (UserService)

    @Column(name = "signer_key_id", length = 36)
    private String signerKeyId; // ID key dùng để ký số (UserKey)

    @Lob
    @Column(name = "signature", nullable = false)
    private byte[] signature; // chữ ký số (detached, OpenPGP format)

    @Column(name = "algorithm", length = 64)
    private String algorithm; // thuật toán ký (vd: RSA, Ed25519…)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // thời điểm ký số
}
