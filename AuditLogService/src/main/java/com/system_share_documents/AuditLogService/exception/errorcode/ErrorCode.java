package com.system_share_documents.AuditLogService.exception.errorcode;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String getCode();
    String getMessage();
    HttpStatus getStatus();
}

