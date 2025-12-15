package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.exception.AppException;

import java.util.Set;

public interface FileValidationService {
    /**
     * Validate file type và content type
     * @param fileBytes Nội dung file
     * @param contentType Content type được khai báo
     * @param originalFilename Tên file gốc
     * @param sizeBytes Kích thước file
     */
    void validateFile(byte[] fileBytes, String contentType, String originalFilename, Long sizeBytes) throws AppException;

    /**
     * Kiểm tra xem file type có được hỗ trợ không
     * @param contentType Content type
     * @return true nếu được hỗ trợ
     */
    boolean isSupportedFileType(String contentType);

    /**
     * Lấy danh sách các content type được hỗ trợ
     * @return Set các content type được hỗ trợ
     */
    Set<String> getSupportedContentTypes();
}
