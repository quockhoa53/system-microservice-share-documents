package com.system_share_documents.UserService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum KeyErrorCode implements ErrorCode {
    UNAUTHORIZED         ("KEY_UNAUTHORIZED",       HttpStatus.UNAUTHORIZED,  "UNAUTHORIZED"),
    FORBIDDEN            ("KEY_FORBIDDEN",          HttpStatus.FORBIDDEN,     "FORBIDDEN"),
    USER_NOT_FOUND       ("KEY_USER_NOT_FOUND",     HttpStatus.NOT_FOUND,     "User not found"),
    KEY_NOT_FOUND        ("KEY_NOT_FOUND",          HttpStatus.NOT_FOUND,     "Key not found"),
    UNSUPPORTED_KEY_TYPE ("KEY_UNSUPPORTED_TYPE",   HttpStatus.BAD_REQUEST,   "Unsupported key type"),
    INVALID_PUBLIC_KEY   ("KEY_INVALID_PUBLIC_KEY", HttpStatus.BAD_REQUEST,   "Invalid armored public key"),
    FINGERPRINT_MISMATCH ("KEY_FP_MISMATCH",        HttpStatus.BAD_REQUEST,   "Fingerprint mismatch");

    private final String code;
    private final HttpStatus status;
    private final String message;

    KeyErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }
}
