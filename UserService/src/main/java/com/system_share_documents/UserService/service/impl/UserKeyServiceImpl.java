package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.dto.request.UploadKeyRequest;
import com.system_share_documents.UserService.dto.response.PublicKeyResponse;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.entity.UserKey;
import com.system_share_documents.UserService.mapper.UserKeyMapper;
import com.system_share_documents.UserService.repository.UserKeyRepository;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.UserKeyService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserKeyServiceImpl implements UserKeyService {

    private final UserRepository userRepository;
    private final UserKeyRepository userKeyRepository;
    private final UserKeyMapper userKeyMapper;

    // Public APIs

    @Override
    @Transactional
    public List<PublicKeyResponse> getMyKeys(Authentication auth) {
        UUID currentUserId = requireCurrentUserId(auth);
        var keys = userKeyRepository.findByUser_Id(currentUserId);
        return userKeyMapper.toResponses(keys);
    }

    @Override
    @Transactional
    public void uploadPublicKeys(UploadKeyRequest req, Authentication auth) {
        UUID callerId = requireCurrentUserId(auth);
        boolean callerIsAdmin = hasAdminRole(auth);

        // Chỉ cho phép upload cho chính mình, trừ khi là admin
        if (!callerIsAdmin && !Objects.equals(callerId, req.getUserId())) {
            throw new RuntimeException("FORBIDDEN: cannot upload keys for another user");
        }

        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean hasNewPrimary = req.getKeys() != null
                && req.getKeys().stream().anyMatch(k -> Boolean.TRUE.equals(k.getPrimary()));
        if (hasNewPrimary) {
            // unset primary cũ
            userKeyRepository.findByUser_Id(user.getId()).forEach(k -> {
                if (Boolean.TRUE.equals(k.getIsPrimary())) k.setIsPrimary(false);
            });
        }

        if (req.getKeys() == null || req.getKeys().isEmpty()) return;

        for (var item : req.getKeys()) {
            validateKeyType(item.getKeyType());

            // BE tính fingerprint từ armored
            String fpActual = extractFingerprintHex(item.getPublicKeyArmored(), item.getKeyType());

            // THAY vì throw, log cảnh báo nếu client gửi khác
            if (!equalsIgnoreCaseHex(fpActual, item.getFingerprint())) {
                System.err.println("[WARN] Fingerprint mismatch. actual=" + fpActual
                        + " provided=" + item.getFingerprint()
                        + " type=" + item.getKeyType());
                // VẪN tiếp tục, dùng fpActual
            }

            // chống trùng
            if (userKeyRepository.existsByUser_IdAndKeyFingerprint(user.getId(), fpActual)) {
                continue;
            }

            UserKey uk = UserKey.builder()
                    .user(user)
                    .keyType(item.getKeyType())
                    .publicKey(item.getPublicKeyArmored())
                    .keyFingerprint(fpActual) // <-- luôn dùng BE-tính
                    .isPrimary(Boolean.TRUE.equals(item.getPrimary()))
                    .build();

            userKeyRepository.save(uk);
        }
    }

    @Override
    @Transactional
    public List<PublicKeyResponse> getKeysOfUser(UUID userId, Authentication auth) {
        if (!hasAdminRole(auth)) throw new RuntimeException("FORBIDDEN");
        var keys = userKeyRepository.findByUser_Id(userId);
        return userKeyMapper.toResponses(keys);
    }

    @Transactional
    @Override
    public List<PublicKeyResponse> getPublicKeysOfUser(UUID userId) {
        var keys = userKeyRepository.findByUser_IdAndRevokedAtIsNull(userId);
        return keys.stream().map(userKeyMapper::toResponse).toList();
    }

    @Transactional
    @Override
    public Optional<PublicKeyResponse> getPublicPrimaryKey(UUID userId, String keyType) {
        return userKeyRepository
                .findFirstByUser_IdAndKeyTypeIgnoreCaseAndIsPrimaryTrueAndRevokedAtIsNull(userId, keyType)
                .map(userKeyMapper::toResponse);
    }

    @Transactional
    @Override
    public List<PublicKeyResponse> getPublicKeysByUsername(String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return getPublicKeysOfUser(user.getId());
    }

    @Transactional
    @Override
    public Optional<PublicKeyResponse> getPublicPrimaryKeyByUsername(String username, String keyType) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return getPublicPrimaryKey(user.getId(), keyType);
    }

    @Transactional
    public void setPrimary(UUID keyId, Authentication auth) {
        UUID userId = requireCurrentUserId(auth);
        UserKey key = userKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("Key not found"));
        if (!key.getUser().getId().equals(userId)) throw new RuntimeException("FORBIDDEN");

        // unset primary cùng keyType
        var siblings = userKeyRepository.findByUser_Id(userId);
        for (var k : siblings) {
            if (k.getKeyType().equalsIgnoreCase(key.getKeyType()) && Boolean.TRUE.equals(k.getIsPrimary())) {
                k.setIsPrimary(false);
            }
        }
        key.setIsPrimary(true);
    }

    @Transactional
    public void revokeKey(UUID keyId, Authentication auth) {
        UUID userId = requireCurrentUserId(auth);
        UserKey key = userKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("Key not found"));
        if (!key.getUser().getId().equals(userId)) throw new RuntimeException("FORBIDDEN");
        key.setRevokedAt(new Timestamp(System.currentTimeMillis()));
        // Optionally: key.setIsPrimary(false);
    }

    //  Helpers

    private void validateKeyType(String keyType) {
        if (!"openpgp-ed25519".equalsIgnoreCase(keyType)
                && !"openpgp-cv25519".equalsIgnoreCase(keyType)) {
            throw new RuntimeException("Unsupported keyType: " + keyType);
        }
    }

    private UUID requireCurrentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new RuntimeException("UNAUTHORIZED");
        }

        // Lấy username từ token rồi map sang DB user.id
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Cannot resolve current user id: missing preferred_username");
        }

        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("User not found by username: " + username));
    }


    private boolean hasAdminRole(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return false;
        // Lấy roles từ realm_access.roles (Keycloak)
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return false;
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object r : roles) {
                    if (r != null && "admin".equalsIgnoreCase(r.toString())) return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    private String extractFingerprintHex(String armoredPublicKey, String keyTypeHint) {
        try {
            byte[] bytes = armoredPublicKey.getBytes(StandardCharsets.UTF_8);
            InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(bytes));
            PGPPublicKeyRingCollection rings = new PGPPublicKeyRingCollection(in, new JcaKeyFingerprintCalculator());

            PGPPublicKey best = null;

            // duyệt tất cả các public keys trong các ring
            for (Iterator<PGPPublicKeyRing> rIt = rings.getKeyRings(); rIt.hasNext();) {
                PGPPublicKeyRing ring = rIt.next();
                for (Iterator<PGPPublicKey> kIt = ring.getPublicKeys(); kIt.hasNext();) {
                    PGPPublicKey k = kIt.next();

                    if (keyMatchesTypeHint(k, keyTypeHint)) {
                        // chọn ngay key phù hợp hint
                        best = k;
                        break;
                    }
                    if (best == null) {
                        // fallback tạm thời, nếu chưa có key phù hợp hint
                        best = k;
                    }
                }
                if (best != null && keyMatchesTypeHint(best, keyTypeHint)) break;
            }

            if (best == null) throw new RuntimeException("No public key found in armored block");

            System.out.println("[DEBUG] PGP key parsed. alg=" + best.getAlgorithm()
                    + " fp=" + toHexLower(best.getFingerprint())
                    + " hint=" + keyTypeHint);
            return toHexLower(best.getFingerprint());
        } catch (Exception e) {
            throw new RuntimeException("Invalid armored public key", e);
        }
    }

    private boolean keyMatchesTypeHint(PGPPublicKey k, String keyTypeHint) {
        if (keyTypeHint == null) return true; // không có hint → chấp nhận mọi key

        boolean wantSign = "openpgp-ed25519".equalsIgnoreCase(keyTypeHint);
        boolean wantEncrypt = "openpgp-cv25519".equalsIgnoreCase(keyTypeHint);

        int alg = k.getAlgorithm();
        int flags = getKeyFlags(k);

        if (wantSign) {
            // Thuật toán thiên về ký
            if (alg == PublicKeyAlgorithmTags.EDDSA     // Ed25519
                    || alg == PublicKeyAlgorithmTags.ECDSA
                    || alg == PublicKeyAlgorithmTags.DSA
                    || alg == PublicKeyAlgorithmTags.RSA_SIGN
                    || alg == PublicKeyAlgorithmTags.RSA_GENERAL) {
                return true;
            }
            // Hoặc có KeyFlags cho ký
            if ((flags & KeyFlags.SIGN_DATA) != 0 || (flags & KeyFlags.CERTIFY_OTHER) != 0) {
                return true;
            }
            return false;
        }

        if (wantEncrypt) {
            // Thuật toán thiên về mã hóa
            if (alg == PublicKeyAlgorithmTags.ECDH      // X25519/cv25519 rơi vào ECDH
                    || alg == PublicKeyAlgorithmTags.RSA_ENCRYPT
                    || alg == PublicKeyAlgorithmTags.RSA_GENERAL
                    || alg == PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT) {
                return true;
            }
            // Hoặc có KeyFlags cho mã hóa
            if ((flags & KeyFlags.ENCRYPT_COMMS) != 0 || (flags & KeyFlags.ENCRYPT_STORAGE) != 0) {
                return true;
            }
            return false;
        }

        // nếu keyTypeHint không phải 2 loại trên, chấp nhận
        return true;
    }

    private int getKeyFlags(PGPPublicKey k) {
        try {
            @SuppressWarnings("unchecked")
            Iterator<PGPSignature> sigIt = k.getSignatures();
            while (sigIt.hasNext()) {
                PGPSignature sig = sigIt.next();
                if (sig.getHashedSubPackets() != null) {
                    int flags = sig.getHashedSubPackets().getKeyFlags();
                    if (flags != 0) return flags;
                }
                if (sig.getUnhashedSubPackets() != null) {
                    int flags = sig.getUnhashedSubPackets().getKeyFlags();
                    if (flags != 0) return flags;
                }
            }
        } catch (Exception ignore) {}
        return 0;
    }

    private static String toHexLower(byte[] bs) {
        StringBuilder sb = new StringBuilder(bs.length * 2);
        for (byte b : bs) sb.append(String.format("%02x", b));
        return sb.toString();
    }
    private static boolean equalsIgnoreCaseHex(String a, String b) {
        if (a == null || b == null) return false;
        return a.replaceAll("\\s", "").equalsIgnoreCase(b.replaceAll("\\s", ""));
    }
}
