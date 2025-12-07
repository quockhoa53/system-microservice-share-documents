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
import java.util.UUID;

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
            String token = jwt.getTokenValue();

            if (username == null || username.isBlank()) {
                throw new AppException(AuthError.INVALID_JWT);
            }

            // Lấy userId từ Keycloak (sub)
            String sub = jwt.getSubject();
            UUID keycloakUserId;
            try {
                keycloakUserId = UUID.fromString(sub);
            } catch (IllegalArgumentException e) {
                // nếu sub không phải UUID hợp lệ
                throw new AppException(AuthError.INVALID_JWT, "Invalid Keycloak subject (not UUID)");
            }

            // Ưu tiên tìm theo id = Keycloak userId
            Optional<User> existingUser = userRepository.findById(keycloakUserId);

            User userEntity;
            if (existingUser.isPresent()) {
                userEntity = existingUser.get();

                // (tuỳ chọn) sync lại thông tin nếu thay đổi trên Keycloak
                boolean changed = false;
                if (email != null && !email.equals(userEntity.getEmail())) {
                    userEntity.setEmail(email);
                    changed = true;
                }
                if (fullName != null && !fullName.equals(userEntity.getFullName())) {
                    userEntity.setFullName(fullName);
                    changed = true;
                }
                if (changed) {
                    userEntity.setUpdatedAt(Timestamp.from(Instant.now()));
                    userEntity = userRepository.save(userEntity);
                }
            } else {
                // Tạo user mới với id = Keycloak userId
                Timestamp now = Timestamp.from(Instant.now());
                userEntity = User.builder()
                        .id(keycloakUserId) // QUAN TRỌNG: id = sub
                        .username(username)
                        .email(email != null ? email : (sub + "@unknown.local"))
                        .fullName(fullName)
                        .status(ACTIVE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                userEntity = userRepository.save(userEntity);
            }

            UserResponse userResponse = userMapper.toResponse(userEntity);
            userResponse.setAccessToken(token);

            return userResponse;

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(SystemError.INTERNAL_ERROR);
        }
    }
}
