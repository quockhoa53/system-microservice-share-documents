package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.KeyErrorCode;
import com.system_share_documents.UserService.service.KeyCryptoService;
import com.system_share_documents.UserService.utils.HexUtils;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

/** Triển khai xử lý PGP: parse key ring, chọn key đúng mục đích, trả fingerprint. */
@Service
public class KeyCryptoServiceImpl implements KeyCryptoService {

    @Override
    public PGPPublicKey readBestPublicKey(String armoredPublicKey, String keyTypeHint) {
        try {
            byte[] bytes = armoredPublicKey.getBytes(StandardCharsets.UTF_8);
            InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(bytes));
            PGPPublicKeyRingCollection rings = new PGPPublicKeyRingCollection(in, new JcaKeyFingerprintCalculator());

            PGPPublicKey best = null;
            for (Iterator<PGPPublicKeyRing> rIt = rings.getKeyRings(); rIt.hasNext();) {
                PGPPublicKeyRing ring = rIt.next();
                for (Iterator<PGPPublicKey> kIt = ring.getPublicKeys(); kIt.hasNext();) {
                    PGPPublicKey k = kIt.next();
                    if (keyMatchesTypeHint(k, keyTypeHint)) {
                        return k; // chọn ngay key phù hợp
                    }
                    if (best == null) best = k; // fallback
                }
            }
            if (best == null) {
                throw new AppException(KeyErrorCode.INVALID_PUBLIC_KEY, "No public key found in armored block");
            }
            return best;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception e) {
            throw new AppException(KeyErrorCode.INVALID_PUBLIC_KEY, "Invalid armored public key");
        }
    }

    @Override
    public String computeFingerprintHex(String armoredPublicKey, String keyTypeHint) {
        PGPPublicKey key = readBestPublicKey(armoredPublicKey, keyTypeHint);
        return HexUtils.toHexLower(key.getFingerprint());
    }

    @Override
    public void validateKeyType(String keyType) {
        if (!"openpgp-ed25519".equalsIgnoreCase(keyType)
                && !"openpgp-cv25519".equalsIgnoreCase(keyType)) {
            throw new AppException(KeyErrorCode.UNSUPPORTED_KEY_TYPE, "Unsupported keyType: " + keyType);
        }
    }

    /** Xác định một PGPPublicKey có phù hợp hint (sign/encrypt) hay không. */
    private boolean keyMatchesTypeHint(PGPPublicKey k, String keyTypeHint) {
        if (keyTypeHint == null) return true;
        boolean wantSign = "openpgp-ed25519".equalsIgnoreCase(keyTypeHint);
        boolean wantEncrypt = "openpgp-cv25519".equalsIgnoreCase(keyTypeHint);

        int alg = k.getAlgorithm();
        int flags = getKeyFlags(k);

        if (wantSign) {
            if (alg == PublicKeyAlgorithmTags.EDDSA
                    || alg == PublicKeyAlgorithmTags.ECDSA
                    || alg == PublicKeyAlgorithmTags.DSA
                    || alg == PublicKeyAlgorithmTags.RSA_SIGN
                    || alg == PublicKeyAlgorithmTags.RSA_GENERAL) return true;
            if ((flags & KeyFlags.SIGN_DATA) != 0 || (flags & KeyFlags.CERTIFY_OTHER) != 0) return true;
            return false;
        }
        if (wantEncrypt) {
            if (alg == PublicKeyAlgorithmTags.ECDH
                    || alg == PublicKeyAlgorithmTags.RSA_ENCRYPT
                    || alg == PublicKeyAlgorithmTags.RSA_GENERAL
                    || alg == PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT) return true;
            if ((flags & KeyFlags.ENCRYPT_COMMS) != 0 || (flags & KeyFlags.ENCRYPT_STORAGE) != 0) return true;
            return false;
        }
        return true;
    }

    /** Lấy KeyFlags từ chữ ký subpacket (nếu có). Không có → trả 0. */
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
}
