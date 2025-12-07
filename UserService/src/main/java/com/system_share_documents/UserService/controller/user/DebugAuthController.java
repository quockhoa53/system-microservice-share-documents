package com.system_share_documents.UserService.controller.user;


import org.springframework.security.core.Authentication;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
public class DebugAuthController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            result.put("error", "No JWT authentication");
            return result;
        }

        // Ở đây đã có biến jwt rồi, KHÔNG khai báo lại
        result.put("preferred_username", jwt.getClaimAsString("preferred_username"));
        result.put("email", jwt.getClaimAsString("email"));
        result.put("realm_access", jwt.getClaim("realm_access"));
        result.put("resource_access", jwt.getClaim("resource_access"));
        result.put("authorities", auth.getAuthorities());

        return result;
    }
}

