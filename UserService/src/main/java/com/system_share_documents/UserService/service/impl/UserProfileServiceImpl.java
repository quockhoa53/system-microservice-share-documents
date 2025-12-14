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

import com.system_share_documents.UserService.utils.SecurityUtils;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private StringRedisTemplate stringRedisTemplate;

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
        
        // Check cache first - read from Redis HASH (synced by Flink CDC job)
        // Pattern: Same as DocumentService - only read cache, let Flink CDC manage cache completely
        // Flink job stores data as: key="user:{userId}", type=HASH (values as plain strings)
        // Flink CDC syncs all data via SNAPSHOT on startup, then real-time via CREATE/UPDATE/DELETE
        try {
            if (stringRedisTemplate != null) {
                String redisKey = "user:" + currentUserId.toString();
                Map<Object, Object> hashData = stringRedisTemplate.opsForHash().entries(redisKey);
                
                if (hashData != null && !hashData.isEmpty()) {
                    try {
                        UserResponse result = convertHashToUserResponse(hashData, currentUserId);
                        if (result != null) {
                            log.info("✅ Cache HIT from Flink CDC Redis HASH for user: userId={}", currentUserId);
                            return result;
                        } else {
                            log.warn("⚠️ Cache data exists but conversion failed for user: userId={}", currentUserId);
                        }
                    } catch (Exception convertEx) {
                        log.warn("Failed to convert Redis HASH to UserResponse, loading from database. Error: {}", 
                            convertEx.getMessage());
                        // Fall through to load from database
                    }
                } else {
                    log.info("❌ Cache MISS - Redis HASH empty or not found for user: userId={}, key={}", currentUserId, redisKey);
                }
            } else {
                log.warn("⚠️ StringRedisTemplate is null, skipping cache read");
            }
        } catch (Exception e) {
            log.warn("Cache read error from Redis HASH, loading from database: {}", e.getMessage());
        }
        
        // Cache miss - fallback to database query
        // Note: Following DocumentService pattern - do NOT write cache here
        // Flink CDC job will automatically sync database changes to Redis HASH
        // Writing here could cause inconsistencies and conflicts with CDC sync
        log.info("📥 Loading user from database (cache miss): userId={}", currentUserId);
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));
        
        UserResponse response = userMapper.toResponse(user);
        
        return response;
    }
    
    /**
     * Convert Redis HASH data (from Flink CDC job) to UserResponse
     * Flink job stores all fields as String values in the HASH
     */
    private UserResponse convertHashToUserResponse(Map<Object, Object> hashData, UUID userId) {
        try {
            UserResponse.UserResponseBuilder builder = UserResponse.builder()
                    .id(userId);
            
            // Extract and convert fields from HASH
            if (hashData.containsKey("username")) {
                builder.username(String.valueOf(hashData.get("username")));
            }
            if (hashData.containsKey("email")) {
                builder.email(String.valueOf(hashData.get("email")));
            }
            // Handle both snake_case (from DB) and camelCase formats
            if (hashData.containsKey("full_name") || hashData.containsKey("fullName")) {
                Object fullNameObj = hashData.getOrDefault("full_name", hashData.get("fullName"));
                String fullNameValue = fullNameObj != null ? String.valueOf(fullNameObj) : null;
                builder.fullName(!"null".equals(fullNameValue) && !fullNameValue.isEmpty() ? fullNameValue : null);
            }
            if (hashData.containsKey("status")) {
                try {
                    String statusStr = String.valueOf(hashData.get("status"));
                    builder.status(Short.parseShort(statusStr));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse status from Redis HASH: {}", hashData.get("status"));
                }
            }
            
            // Parse Timestamps
            // Note: Debezium/PostgreSQL may store timestamps as microseconds (epoch in microseconds)
            // Java Timestamp expects milliseconds, so we need to handle both cases
            if (hashData.containsKey("created_at") || hashData.containsKey("createdAt")) {
                try {
                    String createdAtStr = String.valueOf(hashData.getOrDefault("created_at", hashData.get("createdAt")));
                    if (createdAtStr != null && !createdAtStr.equals("null") && !createdAtStr.isEmpty()) {
                        long timestamp = Long.parseLong(createdAtStr);
                        // PostgreSQL Debezium stores timestamp in microseconds, convert to milliseconds
                        if (timestamp > 1_000_000_000_000_000L) {
                            // Looks like microseconds (very large number), convert to milliseconds
                            timestamp = timestamp / 1000;
                        }
                        builder.createdAt(new Timestamp(timestamp));
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse createdAt from Redis HASH: {}", hashData.get("created_at"));
                }
            }
            if (hashData.containsKey("updated_at") || hashData.containsKey("updatedAt")) {
                try {
                    String updatedAtStr = String.valueOf(hashData.getOrDefault("updated_at", hashData.get("updatedAt")));
                    if (updatedAtStr != null && !updatedAtStr.equals("null") && !updatedAtStr.isEmpty()) {
                        long timestamp = Long.parseLong(updatedAtStr);
                        // PostgreSQL Debezium stores timestamp in microseconds, convert to milliseconds
                        if (timestamp > 1_000_000_000_000_000L) {
                            // Looks like microseconds (very large number), convert to milliseconds
                            timestamp = timestamp / 1000;
                        }
                        builder.updatedAt(new Timestamp(timestamp));
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse updatedAt from Redis HASH: {}", hashData.get("updated_at"));
                }
            }
            
            // Parse profile (stored as JSON string by Flink job, or empty object "{}")
            if (hashData.containsKey("profile")) {
                try {
                    Object profileObj = hashData.get("profile");
                    if (profileObj != null) {
                        String profileStr = String.valueOf(profileObj).trim();
                        
                        // Handle empty/null cases
                        if (profileStr.equals("null") || profileStr.isEmpty() || profileStr.equals("{}")) {
                            builder.profile(new HashMap<>());
                        } else {
                            // Try to parse as JSON string
                            // Flink job stores Map/List as JSON string, simple values as plain string
                            if (profileStr.startsWith("{") && profileStr.endsWith("}")) {
                                // It's a JSON object string, try to parse it
                                try {
                                    if (redisObjectMapper != null) {
                                        Map<String, Object> profileMap = redisObjectMapper.readValue(profileStr, 
                                            new TypeReference<Map<String, Object>>() {});
                                        builder.profile(profileMap != null ? profileMap : new HashMap<>());
                                    } else {
                                        Map<String, Object> profileMap = new ObjectMapper().readValue(profileStr, 
                                            new TypeReference<Map<String, Object>>() {});
                                        builder.profile(profileMap != null ? profileMap : new HashMap<>());
                                    }
                                } catch (Exception jsonEx) {
                                    // If JSON parsing fails, log warning and use empty map
                                    log.warn("Profile value is not valid JSON, using empty map. Value: {}, Error: {}", 
                                        profileStr.length() > 100 ? profileStr.substring(0, 100) + "..." : profileStr, 
                                        jsonEx.getMessage());
                                    builder.profile(new HashMap<>());
                                }
                            } else if (profileObj instanceof Map) {
                                // Already a Map object (shouldn't happen with Redis HASH but handle it)
                                builder.profile((Map<String, Object>) profileObj);
                            } else {
                                // Not a JSON object, treat as plain string value (shouldn't happen but handle gracefully)
                                log.debug("Profile value is not a JSON object, using empty map. Value: {}", 
                                    profileStr.length() > 100 ? profileStr.substring(0, 100) + "..." : profileStr);
                                builder.profile(new HashMap<>());
                            }
                        }
                    } else {
                        builder.profile(new HashMap<>());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse profile from Redis HASH, using empty map. Error: {}", e.getMessage());
                    builder.profile(new HashMap<>());
                }
            } else {
                builder.profile(new HashMap<>());
            }
            
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to convert Redis HASH to UserResponse: {}", e.getMessage(), e);
            return null;
        }
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