package com.system_share_documents.AuditLogService.exception;


import com.system_share_documents.AuditLogService.exception.errorcode.ErrorCode;
import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }


}
