package com.system_share_documents.DocumentService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum NotExistError implements ErrorCode {
    DOCUMENT_NOT_FOUND("DOCUMENT_NOT_FOUND", "Document not found", HttpStatus.BAD_REQUEST),
    VERSION_NOT_FOUND("VERSION_NOT_FOUND", "Document version not found", HttpStatus.BAD_REQUEST),
    DOCUMENT_KEY_NOT_FOUND("DOCUMENT_KEY_NOT_FOUND", "Document key not found", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    NotExistError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
