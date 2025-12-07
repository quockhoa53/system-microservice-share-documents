package com.system_share_documents.AuditLogService.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public class SecurityUtils {

    /**
     * Lấy userId (String UUID) hiện tại từ JWT
     * @param auth Authentication object từ Spring Security
     * @return userId dưới dạng String UUID
     * @throws IllegalStateException nếu không lấy được userId
     */
    public static String requireCurrentUserId(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Authentication is null or not a JWT");
        }

        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new IllegalStateException("JWT subject is null or empty");
        }

        // Validate UUID format
        try {
            UUID.fromString(sub);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT subject is not a valid UUID: " + sub);
        }

        return sub;
    }

    /**
     * Lấy userId từ JWT, trả về null nếu không lấy được
     */
    public static String getCurrentUserId(Authentication auth) {
        try {
            return requireCurrentUserId(auth);
        } catch (Exception e) {
            return null;
        }
    }
}
