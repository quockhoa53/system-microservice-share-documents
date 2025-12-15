package com.system_share_documents.WatermarkWorkerService.utils;

import org.springframework.stereotype.Component;

@Component
public class TypeFileUtils {

    /**
     * Kiểm tra xem file có phải PDF không dựa trên magic bytes
     */
    public static boolean isPdf(byte[] data) {
        return data.length > 4 && data[0] == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46;
    }

    /**
     * Kiểm tra xem file có phải image không dựa trên magic bytes
     * Hỗ trợ: JPEG, PNG, GIF, WebP, BMP, TIFF, ICO
     */
    public static boolean isImage(byte[] data) {
        if (data.length < 4) return false;

        // JPEG: FF D8 FF
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8 && data.length > 2 && data[2] == (byte)0xFF) {
            return true;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (data.length >= 8 && data[0] == (byte)0x89 && data[1] == 0x50 &&
                data[2] == 0x4E && data[3] == 0x47 && data[4] == 0x0D &&
                data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return true;
        }

        // GIF87a: 47 49 46 38 37 61
        if (data.length >= 6 && data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 &&
                data[3] == 0x38 && data[4] == 0x37 && data[5] == 0x61) {
            return true;
        }

        // GIF89a: 47 49 46 38 39 61
        if (data.length >= 6 && data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46 &&
                data[3] == 0x38 && data[4] == 0x39 && data[5] == 0x61) {
            return true;
        }

        // WebP: RIFF...WEBP (52 49 46 46 ... 57 45 42 50)
        if (data.length >= 12 && data[0] == 0x52 && data[1] == 0x49 &&
                data[2] == 0x46 && data[3] == 0x46) {
            String webpCheck = new String(data, 8, 4);
            if ("WEBP".equals(webpCheck)) {
                return true;
            }
        }

        // BMP: 42 4D
        if (data.length >= 2 && data[0] == 0x42 && data[1] == 0x4D) {
            return true;
        }

        // TIFF Little-endian: 49 49 2A 00
        if (data.length >= 4 && data[0] == 0x49 && data[1] == 0x49 &&
                data[2] == 0x2A && data[3] == 0x00) {
            return true;
        }

        // TIFF Big-endian: 4D 4D 00 2A
        if (data.length >= 4 && data[0] == 0x4D && data[1] == 0x4D &&
                data[2] == 0x00 && data[3] == 0x2A) {
            return true;
        }

        // ICO: 00 00 01 00
        if (data.length >= 4 && data[0] == 0x00 && data[1] == 0x00 &&
                data[2] == 0x01 && data[3] == 0x00) {
            return true;
        }

        return false;
    }

    /**
     * Detect image format từ magic bytes
     */
    public static String detectImageFormat(byte[] data) {
        if (data.length < 4) return null;

        // JPEG
        if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) {
            return "jpg";
        }

        // PNG
        if (data.length >= 8 && data[0] == (byte)0x89 && data[1] == 0x50 &&
                data[2] == 0x4E && data[3] == 0x47) {
            return "png";
        }

        // GIF
        if (data.length >= 6 && data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46) {
            return "gif";
        }

        // WebP
        if (data.length >= 12 && data[0] == 0x52 && data[1] == 0x49 &&
                data[2] == 0x46 && data[3] == 0x46) {
            String webpCheck = new String(data, 8, 4);
            if ("WEBP".equals(webpCheck)) {
                return "webp";
            }
        }

        // BMP
        if (data.length >= 2 && data[0] == 0x42 && data[1] == 0x4D) {
            return "bmp";
        }

        // TIFF
        if (data.length >= 4 && ((data[0] == 0x49 && data[1] == 0x49) ||
                (data[0] == 0x4D && data[1] == 0x4D))) {
            return "tiff";
        }

        // ICO
        if (data.length >= 4 && data[0] == 0x00 && data[1] == 0x00 &&
                data[2] == 0x01 && data[3] == 0x00) {
            return "ico";
        }

        return null;
    }

    /**
     * Đoán extension từ content type
     * Hỗ trợ nhiều định dạng: PDF, Images, Office, Code files, Archives
     */
    public static String guessExtension(String contentType) {
        if (contentType == null) {
            return "bin";
        }

        String normalized = contentType.toLowerCase().trim();
        return switch (normalized) {
            // PDF
            case "application/pdf" -> "pdf";
            // Images
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/bmp" -> "bmp";
            case "image/tiff", "image/tif" -> "tiff";
            case "image/x-icon", "image/vnd.microsoft.icon" -> "ico";
            // Microsoft Office (new format)
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            // Microsoft Office (legacy format)
            case "application/msword" -> "doc";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.ms-powerpoint" -> "ppt";
            // Code and Text files
            case "text/plain" -> "txt";
            case "text/java", "text/x-java-source" -> "java";
            case "text/x-python" -> "py";
            case "application/javascript", "text/javascript", "text/x-javascript" -> "js";
            case "application/json", "text/json" -> "json";
            case "text/xml", "application/xml" -> "xml";
            case "text/html" -> "html";
            case "text/css" -> "css";
            case "text/csv" -> "csv";
            case "text/markdown", "text/x-markdown" -> "md";
            // Archives
            case "application/zip", "application/x-zip-compressed" -> "zip";
            case "application/x-rar-compressed" -> "rar";
            case "application/x-tar" -> "tar";
            case "application/gzip" -> "gz";
            default -> "bin";
        };
    }
}
