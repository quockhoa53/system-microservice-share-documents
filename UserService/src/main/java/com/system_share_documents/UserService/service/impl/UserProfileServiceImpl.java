package com.system_share_documents.UserService.service.impl;


import com.system_share_documents.UserService.dto.request.UpdateProfileRequest;
import com.system_share_documents.UserService.dto.response.InternalUserInfoResponse;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.SystemError;
import com.system_share_documents.UserService.mapper.UserMapper;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.UserProfileService;
import com.system_share_documents.UserService.util.SecurityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    // ===== /api/users/me (GET) =====
    @Override
    @Transactional
    public UserResponse getCurrentUser(Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        return userMapper.toResponse(user);
    }

    // ===== PATCH /api/users/me =====
    @Override
    @Transactional
    public UserResponse updateCurrentUserProfile(UpdateProfileRequest request, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        // cập nhật fullName nếu có
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        // merge profile nếu có
        if (request.getProfile() != null) {
            Map<String, Object> existingProfile = user.getProfile();
            if (existingProfile == null) {
                existingProfile = new HashMap<>();
            }
            // merge: key trùng sẽ bị override bởi request
            existingProfile.putAll(request.getProfile());
            user.setProfile(existingProfile);
        }

        user.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    // ===== Internal: GET /internal/users/{userId} =====
    @Override
    @Transactional
    public InternalUserInfoResponse getInternalUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        return InternalUserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .build();
    }

    // ===== Internal: GET /internal/users/by-username/{username} =====
    @Override
    @Transactional
    public InternalUserInfoResponse getInternalUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        return InternalUserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .build();
    }
}