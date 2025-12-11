package com.system_share_documents.UserService.service.impl;


import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.UserService.dto.request.UpdateProfileRequest;
import com.system_share_documents.UserService.dto.response.InternalUserInfoResponse;
import com.system_share_documents.UserService.dto.response.UserBrief;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.SystemError;
import com.system_share_documents.UserService.mapper.UserMapper;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.CacheService;
import com.system_share_documents.UserService.service.UserProfileService;
import com.system_share_documents.UserService.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CacheService cacheService;
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    @Qualifier("redisObjectMapper")
    private ObjectMapper redisObjectMapper;

    @Autowired(required = false)
    private AuditLogProducer auditLogProducer;

    // ===== /api/users/me (GET) =====
    @Override
    @Transactional
    public UserResponse getCurrentUser(Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        
        // Check cache first - use RedisTemplate directly
        try {
            if (redisTemplate != null) {
                String cacheKey = "users::" + currentUserId.toString();
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (cached instanceof String && redisObjectMapper != null) {
                        // Deserialize from JSON string
                        UserResponse result = redisObjectMapper.readValue((String) cached, UserResponse.class);
                        log.debug("Cache HIT for user: userId={}", currentUserId);
                        return result;
                    } else if (cached instanceof UserResponse) {
                        log.debug("Cache HIT for user: userId={}", currentUserId);
                        return (UserResponse) cached;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Cache read error, loading from database: {}", e.getMessage());
        }
        
        // Cache miss - load from database
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));
        
        UserResponse response = userMapper.toResponse(user);
        
        // Store in cache - serialize manually
        try {
            if (redisTemplate != null && redisObjectMapper != null) {
                String cacheKey = "users::" + currentUserId.toString();
                // Serialize to JSON string
                String json = redisObjectMapper.writeValueAsString(response);
                redisTemplate.opsForValue().set(cacheKey, json, java.time.Duration.ofHours(1));
                log.debug("Cache stored for user: userId={}", currentUserId);
            }
        } catch (Exception e) {
            log.warn("Cache write error, continuing without cache: {}", e.getMessage());
        }
        
        return response;
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

        // Evict cache manually to ensure consistency
        cacheService.evictUserCache(currentUserId);
        cacheService.evictUserCacheByUsername(saved.getUsername());

        // Gửi audit log cho UPDATE_PROFILE
        sendUpdateProfileAuditLog(currentUserId.toString(), request);

        return userMapper.toResponse(saved);
    }

    // ===== GET /api/users/search =====
    @Override
    @Transactional
    public List<UserBrief> searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        String searchTerm = query.trim();
        // Limit to 20 results
        Pageable pageable = PageRequest.of(0, 20);
        
        // Search by username, email, or fullName, only active users (status = 1)
        var page = userRepository.findAllWithFilters(searchTerm, (short) 1, pageable);
        
        return page.getContent().stream()
                .map(user -> UserBrief.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .build())
                .collect(Collectors.toList());
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
    @Cacheable(value = "users", key = "#userId.toString()")
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
    @Cacheable(value = "users", key = "'username:' + #username")
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