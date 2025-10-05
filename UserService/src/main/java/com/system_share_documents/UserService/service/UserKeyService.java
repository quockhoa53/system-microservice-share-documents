package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.request.UploadKeyRequest;
import com.system_share_documents.UserService.dto.response.PublicKeyResponse;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserKeyService {
    /** Lấy danh sách public keys (bao gồm revoked) của chính user đang đăng nhập. */
    List<PublicKeyResponse> getMyKeys(Authentication auth);

    /**
     * Upload public keys (client đã sinh). Chỉ tự upload cho mình, trừ admin.
     * Hệ thống tự tính fingerprint, chặn trùng, và xử lý primary.
     */
    void uploadPublicKeys(UploadKeyRequest request, Authentication auth);

    /** Admin: lấy toàn bộ public keys (bao gồm revoked) của user bất kỳ. */
    List<PublicKeyResponse> getKeysOfUser(UUID userId, Authentication auth);

    /** Đặt 1 key của mình làm primary (unset các key cùng keyType). */
    void setPrimary(UUID keyId, Authentication auth);

    /** Đánh dấu revoke một key của mình. */
    void revokeKey(UUID keyId, Authentication auth);

    /** Public: lấy các public keys còn hiệu lực (revokedAt=null) theo userId. */
    List<PublicKeyResponse> getPublicKeysOfUser(UUID userId);

    /** Public: lấy primary public key còn hiệu lực theo userId + keyType. */
    Optional<PublicKeyResponse> getPublicPrimaryKey(UUID userId, String keyType);

    /** Public: lấy các public keys còn hiệu lực theo username. */
    List<PublicKeyResponse> getPublicKeysByUsername(String username);

    /** Public: lấy primary public key còn hiệu lực theo username + keyType. */
    Optional<PublicKeyResponse> getPublicPrimaryKeyByUsername(String username, String keyType);
}
