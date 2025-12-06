package com.system_share_documents.AppCommonService.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import static com.system_share_documents.AppCommonService.constant.KeyCloakConstant.*;

@Component
public class AuthenticationUtils {

    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    public static String getGrantType(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        try {
            DecodedJWT decodedJWT = JWT.decode(token);
            String grantType = decodedJWT.getClaim(GRANT_TYPE).asString();
            if (GRANT_PASSWORD.equals(grantType) || GRANT_CLIENT_CREDENTIALS.equals(grantType)) {
                return grantType;
            }
            String clientId = decodedJWT.getClaim(CLIENT_ID).asString();
            if (CLIENT_ID_SERVICE.equals(clientId)) {
                return GRANT_CLIENT_CREDENTIALS;
            }
            if (CLIENT_ID_USER.equals(clientId)) {
                return GRANT_PASSWORD;
            }
            return null;
        } catch (JWTDecodeException e) {
            return null;
        }
    }
}
