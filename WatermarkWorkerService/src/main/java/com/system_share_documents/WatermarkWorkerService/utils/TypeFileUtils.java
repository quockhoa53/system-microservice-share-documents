package com.system_share_documents.WatermarkWorkerService.utils;

import org.springframework.stereotype.Component;

@Component
public class TypeFileUtils {
    public static boolean isPdf(byte[] data) {
        return data.length > 4 && data[0] == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46;
    }

    public static boolean isImage(byte[] data) {
        if (data.length < 4) return false;
        return (data[0] == (byte)0xFF && data[1] == (byte)0xD8) || // JPEG
                (data[0] == (byte)0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47); // PNG
    }

    public static String guessExtension(String contentType) {
        String type = null;
        if(contentType == null) {
            type = "bin";
            return type;
        }
        type = switch (contentType) {
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            default -> "bin";
        };
        return type;
    }
}
