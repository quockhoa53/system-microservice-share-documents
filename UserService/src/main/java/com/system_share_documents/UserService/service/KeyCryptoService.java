package com.system_share_documents.UserService.service;

import org.bouncycastle.openpgp.PGPPublicKey;

public interface KeyCryptoService {

    /**
     * Đọc armored PGP public key, chọn "best" key theo type hint
     *  ("openpgp-ed25519" => signing; "openpgp-cv25519" => encryption) và trả về PGPPublicKey.
     */
    PGPPublicKey readBestPublicKey(String armoredPublicKey, String keyTypeHint);

    /**
     * Tính fingerprint (hex lower-case) từ armored public key (chọn key theo hint như trên).
     */
    String computeFingerprintHex(String armoredPublicKey, String keyTypeHint);

    /**
     * Kiểm tra keyType có được hỗ trợ hay không (ed25519/cv25519 của OpenPGP).
     */
    void validateKeyType(String keyType);
}