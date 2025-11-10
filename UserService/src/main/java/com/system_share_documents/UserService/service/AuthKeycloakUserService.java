package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.response.UserResponse;
import org.springframework.security.core.Authentication;

public interface AuthKeycloakUserService {
    UserResponse ensureUser(Authentication auth);
}
