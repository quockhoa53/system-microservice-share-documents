package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.service.FileValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.HashSet;

@Service
public class FileValidationServiceImpl implements FileValidationService {

    private static final Logger log = LoggerFactory.getLogger(FileValidationServiceImpl.class);

    // Maximum file size: 100MB (đã được config trong FileUploadConfig)
    private static final long MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024L;

    // Minimum file size để tránh file rỗng hoặc corrupted
    private static final long MIN_FILE_SIZE_BYTES = 100L;

    // Danh sách các content type được hỗ trợ
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            // PDF
            "application/pdf",
            // Images
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/tiff",
            "image/tif",
            "image/x-icon",
            "image/vnd.microsoft.icon",
            // Microsoft Office
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
            "application/msword", // .doc (legacy)
            "application/vnd.ms-excel", // .xls (legacy)
            "application/vnd.ms-powerpoint", // .ppt (legacy)
            // Text and Code files
            "text/plain",
            "text/java",
            "text/x-java-source",
            "text/x-python",
            "application/javascript",
            "text/javascript",
            "text/x-javascript",
            "application/json",
            "text/json",
            "text/xml",
            "application/xml",
            "text/html",
            "text/css",
            "text/csv",
            "text/markdown",
            "text/x-markdown",
            // Other common formats
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/x-tar",
            "application/gzip"
    );

    // Magic bytes cho các file types
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF
    private static final byte[] JPEG_MAGIC = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
    private static final byte[] PNG_MAGIC = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}; // PNG signature
    private static final byte[] GIF_MAGIC_87 = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61}; // GIF87a
    private static final byte[] GIF_MAGIC_89 = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}; // GIF89a
    private static final byte[] WEBP_MAGIC = {0x52, 0x49, 0x46, 0x46}; // RIFF (WebP starts with RIFF)
    private static final byte[] BMP_MAGIC = {0x42, 0x4D}; // BM
    private static final byte[] TIFF_MAGIC_LE = {0x49, 0x49, 0x2A, 0x00}; // II* (little-endian)
    private static final byte[] TIFF_MAGIC_BE = {0x4D, 0x4D, 0x00, 0x2A}; // MM* (big-endian)
    private static final byte[] ICO_MAGIC = {0x00, 0x00, 0x01, 0x00}; // ICO file signature
    // Office files (ZIP-based formats)
    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04}; // PK.. (ZIP/DOCX/XLSX/PPTX)
    private static final byte[] ZIP_MAGIC_ALT = {0x50, 0x4B, 0x05, 0x06}; // PK.. (empty ZIP)
    private static final byte[] ZIP_MAGIC_ALT2 = {0x50, 0x4B, 0x07, 0x08}; // PK.. (ZIP)
    // Legacy Office files
    private static final byte[] DOC_MAGIC = {(byte)0xD0, (byte)0xCF, 0x11, (byte)0xE0, (byte)0xA1, (byte)0xB1, 0x1A, (byte)0xE1}; // .doc, .xls, .ppt
    // Archive files
    private static final byte[] RAR_MAGIC = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07}; // Rar!
    private static final byte[] GZIP_MAGIC = {(byte)0x1F, (byte)0x8B}; // GZIP
    private static final byte[] TAR_MAGIC = {0x75, 0x73, 0x74, 0x61, 0x72}; // ustar (TAR)

    @Override
    public void validateFile(byte[] fileBytes, String contentType, String originalFilename, Long sizeBytes) throws AppException {
        // Validate content type không null
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new AppException(ValidationError.CONTENT_TYPE_REQUIRED);
        }

        // Validate file size
        if (sizeBytes == null || sizeBytes <= 0) {
            throw new AppException(ValidationError.INVALID_FILE_CONTENT, "File size must be greater than 0");
        }

        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ValidationError.FILE_SIZE_EXCEEDED, String.format("File size %d bytes exceeds maximum allowed size %d bytes", sizeBytes, MAX_FILE_SIZE_BYTES));
        }

        if (sizeBytes < MIN_FILE_SIZE_BYTES) {
            throw new AppException(ValidationError.INVALID_FILE_CONTENT, String.format("File size %d bytes is too small, file may be corrupted", sizeBytes));
        }

        // Validate file bytes không null và có kích thước hợp lệ
        if (fileBytes == null || fileBytes.length == 0) {
            throw new AppException(ValidationError.FILE_BYTES_EMPTY);
        }

        if (fileBytes.length != sizeBytes) {
            log.warn("File bytes length {} does not match declared size {}", fileBytes.length, sizeBytes);
        }

        // Validate content type được hỗ trợ
        String normalizedContentType = contentType.toLowerCase().trim();
        if (!isSupportedFileType(normalizedContentType)) {
            throw new AppException(ValidationError.FILE_TYPE_NOT_SUPPORTED, String.format("Content type '%s' is not supported. Supported types: %s", contentType, String.join(", ", SUPPORTED_CONTENT_TYPES)));
        }
        // Validate magic bytes (file signature) để đảm bảo file thực sự là loại được khai báo
        String detectedType = detectFileTypeByMagicBytes(fileBytes);
        if (detectedType == null) {
            throw new AppException(ValidationError.INVALID_FILE_CONTENT, "Cannot determine file type from file content. File may be corrupted or not a supported format.");
        }

        // Kiểm tra content type có khớp với magic bytes không
        if (!isContentTypeMatchesMagicBytes(normalizedContentType, detectedType)) {
            log.warn("Content type mismatch: declared='{}', detected='{}' for file '{}'", contentType, detectedType, originalFilename);
            throw new AppException(ValidationError.FILE_TYPE_MISMATCH, String.format("File content type (%s) does not match declared content type (%s)", detectedType, contentType));
        }

        log.debug("File validation passed: contentType={}, size={}, detectedType={}",
                contentType, sizeBytes, detectedType);
    }

    @Override
    public boolean isSupportedFileType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return false;
        }
        return SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase().trim());
    }

    @Override
    public Set<String> getSupportedContentTypes() {
        return new HashSet<>(SUPPORTED_CONTENT_TYPES);
    }

    /**
     * Detect file type dựa trên magic bytes (file signature)
     * Đây là cách an toàn nhất để xác định loại file thực sự
     */
    private String detectFileTypeByMagicBytes(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length < 4) {
            return null;
        }

        // PDF
        if (startsWith(fileBytes, PDF_MAGIC)) {
            return "application/pdf";
        }

        // JPEG
        if (startsWith(fileBytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }

        // PNG
        if (startsWith(fileBytes, PNG_MAGIC)) {
            return "image/png";
        }

        // GIF
        if (startsWith(fileBytes, GIF_MAGIC_87) || startsWith(fileBytes, GIF_MAGIC_89)) {
            return "image/gif";
        }

        // WebP (RIFF...WEBP)
        if (startsWith(fileBytes, WEBP_MAGIC) && fileBytes.length >= 12) {
            // Check for "WEBP" at offset 8
            String webpCheck = new String(fileBytes, 8, 4);
            if ("WEBP".equals(webpCheck)) {
                return "image/webp";
            }
        }

        // BMP
        if (startsWith(fileBytes, BMP_MAGIC)) {
            return "image/bmp";
        }

        // TIFF
        if (startsWith(fileBytes, TIFF_MAGIC_LE) || startsWith(fileBytes, TIFF_MAGIC_BE)) {
            return "image/tiff";
        }

        // ICO
        if (startsWith(fileBytes, ICO_MAGIC)) {
            return "image/x-icon";
        }

        // ZIP
        if (startsWith(fileBytes, ZIP_MAGIC) || startsWith(fileBytes, ZIP_MAGIC_ALT) || startsWith(fileBytes, ZIP_MAGIC_ALT2)) {
            if (fileBytes.length > 30) {
                String header = new String(fileBytes, 0, Math.min(100, fileBytes.length));
                if (header.contains("word/") || header.contains("xl/") || header.contains("ppt/")) {
                    if (header.contains("word/")) {
                        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    } else if (header.contains("xl/")) {
                        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                    } else if (header.contains("ppt/")) {
                        return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                    }
                }
            }
            return "application/zip";
        }

        // Legacy Office files (.doc, .xls, .ppt)
        if (startsWith(fileBytes, DOC_MAGIC)) {
            return "application/msword";
        }

        // RAR
        if (startsWith(fileBytes, RAR_MAGIC)) {
            return "application/x-rar-compressed";
        }

        // GZIP
        if (startsWith(fileBytes, GZIP_MAGIC)) {
            return "application/gzip";
        }

        // TAR (check at offset 257)
        if (fileBytes.length > 262 && startsWithAtOffset(fileBytes, TAR_MAGIC, 257)) {
            return "application/x-tar";
        }

        // Text files - check if file is mostly ASCII/UTF-8 text
        if (isLikelyTextFile(fileBytes)) {
            return "text/plain";
        }

        return null;
    }

    /**
     * Kiểm tra xem file có phải là text file không (heuristic)
     */
    private boolean isLikelyTextFile(byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            return false;
        }

        // Check first 512 bytes (or entire file if smaller)
        int checkLength = Math.min(512, fileBytes.length);
        int textBytes = 0;
        int nullBytes = 0;

        for (int i = 0; i < checkLength; i++) {
            byte b = fileBytes[i];
            // Check for null bytes (binary files often have nulls)
            if (b == 0) {
                nullBytes++;
            }
            // Check if byte is printable ASCII or common UTF-8
            if ((b >= 0x20 && b <= 0x7E) || b == 0x09 || b == 0x0A || b == 0x0D) {
                textBytes++;
            }
        }

        // If more than 95% are text bytes and no null bytes, likely text file
        if (nullBytes == 0 && textBytes > (checkLength * 0.95)) {
            return true;
        }

        return false;
    }

    /**
     * Helper method để kiểm tra byte array có pattern tại offset cụ thể
     */
    private boolean startsWithAtOffset(byte[] data, byte[] pattern, int offset) {
        if (data == null || pattern == null || data.length < offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Kiểm tra xem content type có khớp với magic bytes không
     */
    private boolean isContentTypeMatchesMagicBytes(String declaredContentType, String detectedType) {
        if (declaredContentType == null || detectedType == null) {
            return false;
        }

        String normalizedDeclared = declaredContentType.toLowerCase().trim();
        String normalizedDetected = detectedType.toLowerCase().trim();

        // Exact match
        if (normalizedDeclared.equals(normalizedDetected)) {
            return true;
        }

        // JPEG variations
        if ((normalizedDeclared.equals("image/jpeg") || normalizedDeclared.equals("image/jpg"))
                && normalizedDetected.equals("image/jpeg")) {
            return true;
        }

        // TIFF variations
        if ((normalizedDeclared.equals("image/tiff") || normalizedDeclared.equals("image/tif"))
                && normalizedDetected.equals("image/tiff")) {
            return true;
        }

        // ICO variations
        if ((normalizedDeclared.equals("image/x-icon") || normalizedDeclared.equals("image/vnd.microsoft.icon"))
                && normalizedDetected.equals("image/x-icon")) {
            return true;
        }

        // ZIP-based Office files - all are ZIP format internally
        if (normalizedDetected.equals("application/zip") || normalizedDetected.startsWith("application/vnd.openxmlformats")) {
            return normalizedDeclared.startsWith("application/vnd.openxmlformats")
                    || normalizedDeclared.equals("application/zip");
        }

        // Legacy Office files - all share same magic bytes
        if (normalizedDetected.equals("application/msword")) {
            return normalizedDeclared.equals("application/msword")
                    || normalizedDeclared.equals("application/vnd.ms-excel")
                    || normalizedDeclared.equals("application/vnd.ms-powerpoint");
        }

        // Text files - allow flexibility for text-based content types
        if (normalizedDetected.equals("text/plain")) {
            return normalizedDeclared.startsWith("text/")
                    || normalizedDeclared.startsWith("application/javascript")
                    || normalizedDeclared.startsWith("application/json")
                    || normalizedDeclared.startsWith("application/xml")
                    || normalizedDeclared.equals("text/plain")
                    || normalizedDeclared.equals("text/java")
                    || normalizedDeclared.equals("text/x-java-source")
                    || normalizedDeclared.equals("text/x-python")
                    || normalizedDeclared.equals("application/javascript")
                    || normalizedDeclared.equals("text/javascript")
                    || normalizedDeclared.equals("text/x-javascript");
        }

        // Archive files
        if (normalizedDetected.equals("application/x-rar-compressed") && normalizedDeclared.equals("application/x-rar-compressed")) {
            return true;
        }
        if (normalizedDetected.equals("application/gzip") && normalizedDeclared.equals("application/gzip")) {
            return true;
        }
        if (normalizedDetected.equals("application/x-tar") && normalizedDeclared.equals("application/x-tar")) {
            return true;
        }

        return false;
    }

    /**
     * Helper method để kiểm tra byte array có bắt đầu với pattern không
     */
    private boolean startsWith(byte[] data, byte[] pattern) {
        if (data == null || pattern == null || data.length < pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }
}

