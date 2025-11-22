package com.system_share_documents.WatermarkWorkerService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ValidationError implements ErrorCode {
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed", HttpStatus.BAD_REQUEST),
    SIGNATURE_INVALID("SIGNATURE_INVALID", "Signature invalid", HttpStatus.BAD_REQUEST),
    CHECKSUM_MISMATCH("CHECKSUM_MISMATCH", "checksum mismatch", HttpStatus.BAD_REQUEST),
    CEK_BYTE_EMPTY("CEK_BYTE_EMPTY", "CEK bytes must not be null or empty", HttpStatus.BAD_REQUEST),
    RECIPIENT_PUBLIC_KEY_EMPTY("RECIPIENT_PUBLIC_KEY_EMPTY", "Recipient public key must not be null or empty", HttpStatus.BAD_REQUEST),
    FILE_BYTES_EMPTY("FILE_BYTES_EMPTY", "Data file bytes must not be empty", HttpStatus.BAD_REQUEST),
    SIGNATURE_EMPTY("SIGNATURE_EMPTY", "Signature must not be empty", HttpStatus.BAD_REQUEST),
    PUBLIC_KEY_EMPTY("PUBLIC_KEY_EMPTY", "Public key must not be empty", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    ValidationError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
