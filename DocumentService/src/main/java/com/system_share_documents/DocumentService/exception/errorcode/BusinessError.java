package com.system_share_documents.DocumentService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BusinessError implements ErrorCode {
    //FAILED TO
    FAILED_GENERATE_AES("FAILED_GENERATE_AES", "Failed to generate AES key", HttpStatus.BAD_REQUEST),
    FAILED_CHECKSUM("FAILED_CHECKSUM", "Failed to calculate checksum", HttpStatus.BAD_REQUEST),
    FAILED_WRAP_CEK("FAILED_WRAP_CEK" , "Failed to wrap CEK for recipient", HttpStatus.BAD_REQUEST),
    FAILED_INIT_UPLOAD("FAILED_INIT_UPLOAD" , "Failed to initialize upload", HttpStatus.BAD_REQUEST),;

    private final String code;
    private final String message;
    private final HttpStatus status;

    BusinessError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
