package com.system_share_documents.DocumentService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ValidationError implements ErrorCode {
    INVALID_PARAM("INVALID_PARAM", "Invalid validated param", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation failed", HttpStatus.BAD_REQUEST),
    SIGNATURE_INVALID("SIGNATURE_INVALID", "Signature invalid", HttpStatus.BAD_REQUEST),
    CHECKSUM_MISMATCH("CHECKSUM_MISMATCH", "checksum mismatch", HttpStatus.BAD_REQUEST),
    CEK_BYTE_EMPTY("CEK_BYTE_EMPTY", "CEK bytes must not be null or empty", HttpStatus.BAD_REQUEST),
    RECIPIENT_PUBLIC_KEY_EMPTY("RECIPIENT_PUBLIC_KEY_EMPTY", "Recipient public key must not be null or empty", HttpStatus.BAD_REQUEST),
    FILE_BYTES_EMPTY("FILE_BYTES_EMPTY", "Data file bytes must not be empty", HttpStatus.BAD_REQUEST),
    SIGNATURE_EMPTY("SIGNATURE_EMPTY", "Signature must not be empty", HttpStatus.BAD_REQUEST),
    PUBLIC_KEY_EMPTY("PUBLIC_KEY_EMPTY", "Public key must not be empty", HttpStatus.BAD_REQUEST),
    VERSION_IDS_EMPTY("VERSION_IDS_EMPTY", "Version IDs list cannot be empty", HttpStatus.BAD_REQUEST),
    CAN_NOT_UPLOAD_BY_STATUS("CAN_NOT_UPLOAD_BY_STATUS" , "Can only reinitialize upload for versions with UPLOADING status", HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_SUPPORTED("FILE_TYPE_NOT_SUPPORTED", "File type is not supported. Only PDF and image files (JPEG, PNG, GIF, WebP, BMP, TIFF) are allowed", HttpStatus.BAD_REQUEST),
    FILE_TYPE_MISMATCH("FILE_TYPE_MISMATCH", "File content does not match declared content type", HttpStatus.BAD_REQUEST),
    FILE_SIZE_EXCEEDED("FILE_SIZE_EXCEEDED", "File size exceeds maximum allowed size", HttpStatus.BAD_REQUEST),
    INVALID_FILE_CONTENT("INVALID_FILE_CONTENT", "File content is invalid or corrupted", HttpStatus.BAD_REQUEST),
    CONTENT_TYPE_REQUIRED("CONTENT_TYPE_REQUIRED", "Content type is required", HttpStatus.BAD_REQUEST),
    GROUP_ID_EMPTY("GROUP_ID_EMPTY", "GroupId must not be empty", HttpStatus.BAD_REQUEST),
    DOCUMENT_ALREADY_UPLOADED("DOCUMENT_ALREADY_UPLOADED", "This document has already been uploaded previously", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    ValidationError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
