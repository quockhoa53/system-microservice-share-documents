package com.system_share_documents.DocumentService.exception.errorcode;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum BusinessError implements ErrorCode {
    //FAILED TO
    FAILED_GENERATE_AES("FAILED_GENERATE_AES", "Failed to generate AES key", HttpStatus.BAD_REQUEST),
    FAILED_CHECKSUM("FAILED_CHECKSUM", "Failed to calculate checksum", HttpStatus.BAD_REQUEST),
    FAILED_WRAP_CEK("FAILED_WRAP_CEK" , "Failed to wrap CEK with recipient public key", HttpStatus.BAD_REQUEST),
    FAILED_INIT_UPLOAD("FAILED_INIT_UPLOAD" , "Failed to initialize upload", HttpStatus.BAD_REQUEST),
    FAILED_COMPLETED_UPLOAD("FAILED_COMPLETED_UPLOAD" , "Failed to completed upload", HttpStatus.BAD_REQUEST),
    FAILED_CREATE_KEY("FAILED_CREATE_KEY" , "Failed to create and save key", HttpStatus.BAD_REQUEST),
    FAILED_DOWNLOAD_DOCUMENT("FAILED_DOWNLOAD_DOCUMENT" , "Failed to download document", HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_YET_WATERMARK("DOCUMENT_NOT_YET_WATERMARK" , "Document version has not been watermarked yet", HttpStatus.BAD_REQUEST),
    DOCUMENT_NOT_YET_AVAILABLE("DOCUMENT_NOT_YET_AVAILABLE" , "Document version must be in status available", HttpStatus.BAD_REQUEST),
    FAILED_GET_ENCRYPTION("FAILED_GET_ENCRYPTION" , "Failed to get encryption key", HttpStatus.BAD_REQUEST),
    USER_NOT_PERMISSION("USER_NOT_PERMISSION" , "The user does not have permission to perform this action on the document", HttpStatus.BAD_REQUEST),
    FAILED_DELETE_VERSION("FAILED_DELETE_VERSION" , "Failed to delete list versions of document", HttpStatus.BAD_REQUEST),
    FAILED_DELETE_DOCUMENT("FAILED_DELETE_DOCUMENT" , "Failed to delete document", HttpStatus.BAD_REQUEST),
    FAILED_PREVIEW_DOCUMENT("FAILED_PREVIEW_DOCUMENT" , "Failed to preview document", HttpStatus.BAD_REQUEST),
    FAILED_REINIT_UPLOAD("FAILED_REINIT_UPLOAD" , "Failed to reinit upload document", HttpStatus.BAD_REQUEST),
    USER_IS_OWNER("USER_IS_OWNER" , "User is already the owner of this document", HttpStatus.BAD_REQUEST),
    USER_ALREADY_HAS_ACCESS("USER_ALREADY_HAS_ACCESS" , "User already has access to this document", HttpStatus.BAD_REQUEST),
    FAILED_REQUEST_ACCESS("FAILED_REQUEST_ACCESS" , "Failed to request access to document", HttpStatus.BAD_REQUEST),
    FAILED_DISSOLVE_GROUP("FAILED_DISSOLVE_GROUP" , "Failed to dissolve group documents", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    BusinessError(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
