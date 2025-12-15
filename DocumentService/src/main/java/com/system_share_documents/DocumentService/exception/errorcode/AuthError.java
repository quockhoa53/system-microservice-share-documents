package com.system_share_documents.DocumentService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AuthError implements ErrorCode {
    UNAUTHORIZED("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN),
    INVALID_JWT("INVALID_JWT", "Invalid JWT token", HttpStatus.BAD_REQUEST),
    FORBIDDEN_ACTION_DELETE("FORBIDDEN_ACTION_DELETE", "Action delete document is denied, user be not must owner's document", HttpStatus.BAD_REQUEST),
    FORBIDDEN_ACTION_UPLOAD("FORBIDDEN_ACTION_UPLOAD", "Action update document is denied, user be not must owner's document", HttpStatus.BAD_REQUEST),
    FORBIDDEN_ACTION_ADD("FORBIDDEN_ACTION_ADD", "Action add document is denied, user be not must owner's document", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    AuthError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
