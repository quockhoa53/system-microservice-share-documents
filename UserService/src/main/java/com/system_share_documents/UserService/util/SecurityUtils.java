package com.system_share_documents.UserService.util;

// hoặc package nơi AuthError của bạn nằm
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.AuthError;
import com.system_share_documents.UserService.repository.UserRepository;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Helper cho các thao tác trích xuất userId/role từ Authentication (Keycloak JWT). */
@UtilityClass
public class SecurityUtils {

    /**
     * Lấy userId (UUID) hiện tại từ JWT:
     * - Lấy preferred_username từ token
     * - Map sang DB (users.username -> users.id)
     * Ném AppException(UNAUTHORIZED) nếu không hợp lệ.
     */
    public static UUID requireCurrentUserId(Authentication auth, UserRepository userRepository) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new AppException(AuthError.UNAUTHORIZED);
        }
        String username = jwt.getClaimAsString("preferred_username");
        if (username == null || username.isBlank()) {
            throw new AppException(AuthError.UNAUTHORIZED, "Missing preferred_username");
        }
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new AppException(AuthError.UNAUTHORIZED, "User not found: " + username));
    }

    /**
     * Kiểm tra user có role "admin" trong realm_access.roles (Keycloak) hay không.
     */
    public static boolean hasAdminRole(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) return false;
        try {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) return false;
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                for (Object r : roles) {
                    if (r != null && "admin".equalsIgnoreCase(r.toString())) return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    /**
     * Bắt buộc là admin, nếu không ném UNAUTHORIZED/ FORBIDDEN (tuỳ bạn chọn).
     */
    public static void requireAdmin(Authentication auth) {
        if (!hasAdminRole(auth)) {
            // Có thể là UNAUTHORIZED hoặc FORBIDDEN tuỳ triết lý hệ thống
            throw new AppException(AuthError.FORBIDDEN, "Admin role required");
        }
    }
}
