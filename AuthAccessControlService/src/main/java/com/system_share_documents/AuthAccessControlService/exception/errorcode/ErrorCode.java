package com.system_share_documents.AuthAccessControlService.exception.errorcode;

import org.springframework.http.HttpStatus;

public interface ErrorCode {
    String getCode();
    String getMessage();
    HttpStatus getStatus();
}

