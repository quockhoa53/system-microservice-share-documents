package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.AuthError;
import com.system_share_documents.UserService.exception.errorcode.SystemError;
import com.system_share_documents.UserService.mapper.UserMapper;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.AuthKeycloakUserService;
import com.system_share_documents.UserService.service.UserKeyService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static com.system_share_documents.UserService.constant.JwtClaims.*;
import static com.system_share_documents.UserService.constant.UserStatus.ACTIVE;

@Service
public class AuthKeycloakUserServiceImpl implements AuthKeycloakUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserKeyService userKeyService;

    @Override
    @Transactional
    public UserResponse ensureUser(Authentication auth) {
        try {
            if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
                throw new AppException(AuthError.UNAUTHORIZED);
            }
            String username = jwt.getClaimAsString(PREFERRED_USERNAME);
            String email = jwt.getClaimAsString(EMAIL);
            String fullName = jwt.getClaimAsString(NAME);
            if (username == null || username.isBlank()) {
                throw new AppException(AuthError.INVALID_JWT);
            }
            Optional<User> existingUser = userRepository.findByUsername(username);
            if (existingUser.isPresent()) {
                return userMapper.toResponse(existingUser.get());
            }
            User newUser = User.builder()
                    .username(username)
                    .email(email != null ? email : (jwt.getSubject() + "@unknown.local"))
                    .fullName(fullName)
                    .status(ACTIVE)
                    .createdAt(Timestamp.from(Instant.now()))
                    .updatedAt(Timestamp.from(Instant.now()))
                    .build();
            User savedUser = userRepository.save(newUser);
            return userMapper.toResponse(savedUser);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(SystemError.INTERNAL_ERROR);
        }
    }
}
