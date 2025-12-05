package com.system_share_documents.AuditLogService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BusinessError implements ErrorCode {
    // USER NOT FOUND
    USER_NOT_FOUND("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND),
    // DOCUMENT NOT FOUND
    // USER ALREADY EXISTS
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "Email already exists", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;

    BusinessError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
