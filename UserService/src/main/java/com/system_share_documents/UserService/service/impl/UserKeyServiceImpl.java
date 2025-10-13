package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.dto.request.UploadKeyRequest;
import com.system_share_documents.UserService.dto.response.PublicKeyResponse;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.entity.UserKey;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.KeyErrorCode;
import com.system_share_documents.UserService.mapper.UserKeyMapper;
import com.system_share_documents.UserService.repository.UserKeyRepository;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.KeyCryptoService;
import com.system_share_documents.UserService.service.UserKeyService;
import com.system_share_documents.UserService.utils.HexUtils;
import com.system_share_documents.UserService.utils.SecurityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserKeyServiceImpl implements UserKeyService {

    private final UserRepository userRepository;
    private final UserKeyRepository userKeyRepository;
    private final UserKeyMapper userKeyMapper;
    private final KeyCryptoService keyCryptoService;

    /** Trả về danh sách public keys (bao gồm revoked) của user hiện tại. */
    @Override
    @Transactional
    public List<PublicKeyResponse> getMyKeys(Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        var keys = userKeyRepository.findByUser_Id(currentUserId);
        return userKeyMapper.toResponses(keys);
    }

    /**
     * Upload các public keys cho user (client-side sinh). Chỉ tự upload cho mình, trừ admin.
     * Hệ thống tự parse armored để tính fingerprint, chặn trùng fingerprint.
     * Nếu payload có key primary mới, sẽ unset primary cũ (cùng user).
     */
    @Override
    @Transactional
    public void uploadPublicKeys(UploadKeyRequest req, Authentication auth) {
        UUID callerId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        boolean callerIsAdmin = SecurityUtils.hasAdminRole(auth);

        if (!callerIsAdmin && !Objects.equals(callerId, req.getUserId())) {
            throw new AppException(KeyErrorCode.FORBIDDEN, "FORBIDDEN: cannot upload keys for another user");
        }

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new AppException(KeyErrorCode.USER_NOT_FOUND));

        boolean hasNewPrimary = req.getKeys() != null
                && req.getKeys().stream().anyMatch(k -> Boolean.TRUE.equals(k.getPrimary()));
        if (hasNewPrimary) {
            userKeyRepository.findByUser_Id(user.getId())
                    .forEach(k -> { if (Boolean.TRUE.equals(k.getIsPrimary())) k.setIsPrimary(false); });
        }

        if (req.getKeys() == null || req.getKeys().isEmpty()) return;

        for (var item : req.getKeys()) {
            keyCryptoService.validateKeyType(item.getKeyType());
            // BE tính fingerprint từ armored (ưu tiên key theo hint)
            String fpActual = keyCryptoService.computeFingerprintHex(item.getPublicKeyArmored(), item.getKeyType());

            // Nếu client gửi fingerprint khác -> log cảnh báo, nhưng dùng fpActual
            if (!HexUtils.equalsIgnoreCaseHex(fpActual, item.getFingerprint())) {
                System.err.printf("[WARN] Fingerprint mismatch. actual=%s provided=%s type=%s%n",
                        fpActual, item.getFingerprint(), item.getKeyType());
            }

            if (userKeyRepository.existsByUser_IdAndKeyFingerprint(user.getId(), fpActual)) {
                continue; // đã có, bỏ qua
            }

            UserKey uk = UserKey.builder()
                    .user(user)
                    .keyType(item.getKeyType())
                    .publicKey(item.getPublicKeyArmored())
                    .keyFingerprint(fpActual)
                    .isPrimary(Boolean.TRUE.equals(item.getPrimary()))
                    .createdAt(new Timestamp(System.currentTimeMillis()))
                    .build();

            userKeyRepository.save(uk);
        }
    }

    /** Admin: xem toàn bộ public keys (bao gồm revoked) của một user bất kỳ. */
    @Override
    @Transactional
    public List<PublicKeyResponse> getKeysOfUser(UUID userId, Authentication auth) {
        if (!SecurityUtils.hasAdminRole(auth)) throw new AppException(KeyErrorCode.FORBIDDEN);
        var keys = userKeyRepository.findByUser_Id(userId);
        return userKeyMapper.toResponses(keys);
    }

    /** Public: trả các public keys còn hiệu lực (revokedAt = null) của user theo userId. */
    @Override
    @Transactional
    public List<PublicKeyResponse> getPublicKeysOfUser(UUID userId) {
        var keys = userKeyRepository.findByUser_IdAndRevokedAtIsNull(userId);
        return keys.stream().map(userKeyMapper::toResponse).toList();
    }

    /** Public: lấy primary public key (revokedAt = null) theo userId + keyType. */
    @Override
    @Transactional
    public Optional<PublicKeyResponse> getPublicPrimaryKey(UUID userId, String keyType) {
        return userKeyRepository
                .findFirstByUser_IdAndKeyTypeIgnoreCaseAndIsPrimaryTrueAndRevokedAtIsNull(userId, keyType)
                .map(userKeyMapper::toResponse);
    }

    /** Public: lấy các public keys còn hiệu lực theo username. */
    @Override
    @Transactional
    public List<PublicKeyResponse> getPublicKeysByUsername(String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(KeyErrorCode.USER_NOT_FOUND));
        return getPublicKeysOfUser(user.getId());
    }

    /** Public: lấy primary key theo username + keyType. */
    @Override
    @Transactional
    public Optional<PublicKeyResponse> getPublicPrimaryKeyByUsername(String username, String keyType) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(KeyErrorCode.USER_NOT_FOUND));
        return getPublicPrimaryKey(user.getId(), keyType);
    }

    /** Đặt 1 key của mình làm primary (unset các key cùng keyType). */
    @Override
    @Transactional
    public void setPrimary(UUID keyId, Authentication auth) {
        UUID userId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        UserKey key = userKeyRepository.findById(keyId)
                .orElseThrow(() -> new AppException(KeyErrorCode.KEY_NOT_FOUND));
        if (!key.getUser().getId().equals(userId)) throw new AppException(KeyErrorCode.FORBIDDEN);

        var siblings = userKeyRepository.findByUser_Id(userId);
        for (var k : siblings) {
            if (k.getKeyType().equalsIgnoreCase(key.getKeyType()) && Boolean.TRUE.equals(k.getIsPrimary())) {
                k.setIsPrimary(false);
            }
        }
        key.setIsPrimary(true);
    }

    /** Revoke một key của mình (đánh dấu revokedAt); có thể bỏ cờ primary nếu muốn. */
    @Override
    @Transactional
    public void revokeKey(UUID keyId, Authentication auth) {
        UUID userId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        UserKey key = userKeyRepository.findById(keyId)
                .orElseThrow(() -> new AppException(KeyErrorCode.KEY_NOT_FOUND));
        if (!key.getUser().getId().equals(userId)) throw new AppException(KeyErrorCode.FORBIDDEN);
        key.setRevokedAt(new Timestamp(System.currentTimeMillis()));
    }
}
