package com.system_share_documents.UserService.utils;

import lombok.experimental.UtilityClass;

/** Tiện ích xử lý hex/string. */
@UtilityClass
public class HexUtils {
    /** Chuyển bytes -> hex lower-case. */
    public static String toHexLower(byte[] bs) {
        StringBuilder sb = new StringBuilder(bs.length * 2);
        for (byte b : bs) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** So sánh 2 chuỗi hex, bỏ khoảng trắng, không phân biệt hoa thường. */
    public static boolean equalsIgnoreCaseHex(String a, String b) {
        if (a == null || b == null) return false;
        return a.replaceAll("\\s", "").equalsIgnoreCase(b.replaceAll("\\s", ""));
    }
}
