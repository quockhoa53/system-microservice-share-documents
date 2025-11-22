package com.system_share_documents.AuthAccessControlService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BusinessError implements ErrorCode {
    FAILED_GRANT_ACCESS("FAILED_GRANT_ACCESS", "Failed to grant access for recipient", HttpStatus.BAD_REQUEST),
    FAILED_REVOKE_GRANT("FAILED_REVOKE_GRANT", "Failed to revoke grant access for recipient", HttpStatus.BAD_REQUEST),
    FAILED_GET_RECIPIENTS("FAILED_GET_RECIPIENTS", "Failed to get list recipient belongs to a document", HttpStatus.BAD_REQUEST),
    FAILED_CHECK_GRANT("FAILED_CHECK_GRANT", "Failed to check grant access for recipient in document", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    BusinessError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
