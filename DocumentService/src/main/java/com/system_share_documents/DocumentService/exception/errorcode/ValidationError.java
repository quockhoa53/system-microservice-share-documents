package com.system_share_documents.DocumentService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ValidationError implements ErrorCode {
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed", HttpStatus.BAD_REQUEST),
    SIGNATURE_INVALID("SIGNATURE_INVALID", "Signature invalid", HttpStatus.BAD_REQUEST),
    CHECKSUM_MISMATCH("CHECKSUM_MISMATCH", "checksum mismatch", HttpStatus.BAD_REQUEST),
    FILE_BYTE_EMPTY("FILE_BYTE_EMPTY", "File not found or unavailable in storage (Minio)", HttpStatus.BAD_REQUEST),
    SINGER_PUBLIC_KEY_EMPTY("SINGER_PUBLIC_KEY_EMPTY", "Singer public key not found", HttpStatus.BAD_REQUEST),
    USER_PUBLIC_KEY_EMPTY("USER_PUBLIC_KEY_EMPTY", "User public key not found", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    ValidationError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
