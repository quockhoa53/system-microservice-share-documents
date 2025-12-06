package com.system_share_documents.DocumentService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum NotExistError implements ErrorCode {
    DOCUMENT_NOT_FOUND("DOCUMENT_NOT_FOUND", "Document not found", HttpStatus.BAD_REQUEST),
    VERSION_NOT_FOUND("VERSION_NOT_FOUND", "Document version not found", HttpStatus.BAD_REQUEST),
    DOCUMENT_KEY_NOT_FOUND("DOCUMENT_KEY_NOT_FOUND", "Document key not found", HttpStatus.BAD_REQUEST),
    FILE_BYTE_EMPTY("FILE_BYTE_EMPTY", "File not found or unavailable in storage (Minio)", HttpStatus.BAD_REQUEST),
    SINGER_PUBLIC_KEY_EMPTY("SINGER_PUBLIC_KEY_EMPTY", "Singer public key not found", HttpStatus.BAD_REQUEST),
    USER_PUBLIC_KEY_EMPTY("USER_PUBLIC_KEY_EMPTY", "User public key not found", HttpStatus.BAD_REQUEST),
    ENCRYPTION_KEY_NOT_FOUND("ENCRYPTION_KEY_NOT_FOUND", "No encryption key found in recipient public key", HttpStatus.BAD_REQUEST),
    PUBLIC_KEY_SIGNATURE_NOT_FOUND("PUBLIC_KEY_SIGNATURE_NOT_FOUND", "Public key for signature not found in keyring", HttpStatus.BAD_REQUEST),
    DOCUMENT_KEY_EXITS("DOCUMENT_KEY_EXITS", "Document key already exits", HttpStatus.BAD_REQUEST),
    CEK_NOT_FOUND("CEK_NOT_FOUND", "Owner CEK not found", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    NotExistError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
