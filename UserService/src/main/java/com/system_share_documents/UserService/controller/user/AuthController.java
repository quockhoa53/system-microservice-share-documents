package com.system_share_documents.UserService.controller.user;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.service.AuthKeycloakUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/")
public class AuthController {

    @Autowired
    private AuthKeycloakUserService authKeycloakUserService;

    @GetMapping("login")
    public ApiResponse<UserResponse> login(Authentication auth) {
        UserResponse userResponse = authKeycloakUserService.ensureUser(auth);
        return ApiResponse.success("OK", "Login successful", userResponse);
    }
}