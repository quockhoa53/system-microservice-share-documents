package com.system_share_documents.UserService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SystemError implements ErrorCode {
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("DATABASE_ERROR", "Database operation failed", HttpStatus.INTERNAL_SERVER_ERROR),
    NOT_FOUND("NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    INVALID_PARAM("INVALID_PARAM", "Invalid parameter", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("INVALID_REQUEST", "Invalid request", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    SystemError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
