package com.system_share_documents.UserService.service.impl;


import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Autowired(required = false)
    private AuditLogProducer auditLogProducer;

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

        // Gửi audit log cho UPDATE_PROFILE
        sendUpdateProfileAuditLog(currentUserId.toString(), request);

        return userMapper.toResponse(saved);
    }

    /**
     * Helper method để gửi audit log cho UPDATE_PROFILE
     */
    private void sendUpdateProfileAuditLog(String userId, UpdateProfileRequest request) {
        if (auditLogProducer == null) {
            return;
        }

        try {
            HttpServletRequest httpRequest = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                httpRequest = attributes.getRequest();
            }

            String ip = httpRequest != null ? getClientIp(httpRequest) : "unknown";
            String userAgent = httpRequest != null ? getUserAgent(httpRequest) : "unknown";

            Map<String, Object> metadata = new HashMap<>();
            if (request.getFullName() != null) {
                metadata.put("fullName", request.getFullName());
            }
            if (request.getProfile() != null) {
                metadata.put("profile", request.getProfile());
            }

            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(userId)
                    .action(String.valueOf(ActionLog.UPDATE_PROFILE))
                    .objectType("user")
                    .typeLog("USER")
                    .status("OK")
                    .errorReason(null)
                    .ip(ip)
                    .userAgent(userAgent)
                    .metadata(convertMetadataToJson(metadata))
                    .timestamp(Instant.now())
                    .build();

            auditLogProducer.sendAuditLog(logEvent, userId);
        } catch (Exception e) {
            // Ignore audit log errors
        }
    }

    private String convertMetadataToJson(Map<String, Object> metadata) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
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