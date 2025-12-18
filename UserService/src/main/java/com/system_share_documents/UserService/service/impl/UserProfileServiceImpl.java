package com.system_share_documents.UserService.service.impl;


import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.UserService.dto.request.ChangePasswordRequest;
import com.system_share_documents.UserService.dto.request.UpdateProfileRequest;
import com.system_share_documents.UserService.dto.response.InternalUserInfoResponse;
import com.system_share_documents.UserService.dto.response.UserBrief;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.AuthError;
import com.system_share_documents.UserService.exception.errorcode.SystemError;
import com.system_share_documents.UserService.mapper.UserMapper;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.CacheService;
import com.system_share_documents.UserService.service.UserProfileService;

import com.system_share_documents.UserService.utils.SecurityUtils;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import jakarta.ws.rs.WebApplicationException;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired(required = false)
    private Keycloak keycloakAdmin;

    @Value("${app.security.keycloak.realm:system-share-docs}")
    private String keycloakRealm;

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
            // Handle avatar
            if (hashData.containsKey("avatar")) {
                Object avatarObj = hashData.get("avatar");
                String avatarValue = avatarObj != null ? String.valueOf(avatarObj) : null;
                builder.avatar(!"null".equals(avatarValue) && !avatarValue.isEmpty() ? avatarValue : null);
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

        // cập nhật avatar nếu có
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
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

        // Đồng bộ ngược lại lên Keycloak
        syncUserToKeycloak(saved, request);

        // Evict cache manually to ensure consistency
        cacheService.evictUserCache(currentUserId);
        cacheService.evictUserCacheByUsername(saved.getUsername());

        // Gửi audit log cho UPDATE_PROFILE
        sendUpdateProfileAuditLog(currentUserId.toString(), request);

        return userMapper.toResponse(saved);
    }

    // ===== PUT /api/users/me/password =====
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        // Validate old password bằng cách thử login với Keycloak
        validateCurrentPassword(user.getUsername(), request.getCurrentPassword());

        // Update password mới trong Keycloak
        updatePasswordInKeycloak(currentUserId, request.getNewPassword());

        // Gửi audit log
        sendChangePasswordAuditLog(currentUserId.toString());
    }

    /**
     * Validate current password bằng cách thử login với Keycloak
     */
    private void validateCurrentPassword(String username, String password) {
        try {
            // Lấy thông tin từ config
            String keycloakUrl = getKeycloakServerUrl();
            String realm = keycloakRealm;
            String clientId = getKeycloakClientId();
            String clientSecret = getKeycloakClientSecret();

            // Gọi Keycloak token endpoint để validate password
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", 
                keycloakUrl, realm);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "password");
            body.add("username", username);
            body.add("password", password);
            body.add("client_id", clientId);
            if (clientSecret != null && !clientSecret.isEmpty()) {
                body.add("client_secret", clientSecret);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
                if (response.getStatusCode() != HttpStatus.OK) {
                    throw new AppException(AuthError.INVALID_PASSWORD);
                }
                // Nếu thành công thì password đúng
                log.debug("Current password validated successfully for user: {}", username);
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || 
                    e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                    log.warn("Invalid current password for user: {}", username);
                    throw new AppException(AuthError.INVALID_PASSWORD);
                }
                throw new AppException(SystemError.INTERNAL_ERROR, 
                    "Failed to validate password: " + e.getMessage());
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating current password: {}", e.getMessage(), e);
            throw new AppException(SystemError.INTERNAL_ERROR, 
                "Failed to validate password: " + e.getMessage());
        }
    }

    /**
     * Update password mới trong Keycloak
     */
    private void updatePasswordInKeycloak(UUID userId, String newPassword) {
        if (keycloakAdmin == null) {
            log.error("Keycloak admin client is not available, cannot change password");
            throw new AppException(SystemError.INTERNAL_ERROR, 
                "Keycloak admin client is not configured");
        }

        try {
            RealmResource realm = keycloakAdmin.realm(keycloakRealm);
            UserResource userResource = realm.users().get(userId.toString());

            // Tạo CredentialRepresentation cho password mới
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(newPassword);
            credential.setTemporary(false); // Password không phải temporary

            // Reset password trong Keycloak
            userResource.resetPassword(credential);

            log.info("✅ Successfully changed password in Keycloak for user: {}", userId);
        } catch (jakarta.ws.rs.WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                log.error("User {} not found in Keycloak, cannot change password", userId);
                throw new AppException(SystemError.NOT_FOUND, 
                    "User not found in Keycloak");
            } else {
                log.error("Keycloak API error while changing password for user {}: {} (status: {})", 
                    userId, e.getMessage(), 
                    e.getResponse() != null ? e.getResponse().getStatus() : "unknown");
                throw new AppException(SystemError.INTERNAL_ERROR, 
                    "Failed to change password in Keycloak: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to change password in Keycloak for user {}: {}", userId, e.getMessage(), e);
            throw new AppException(SystemError.INTERNAL_ERROR, 
                "Failed to change password: " + e.getMessage());
        }
    }

    /**
     * Helper method để gửi audit log cho CHANGE_PASSWORD
     */
    private void sendChangePasswordAuditLog(String userId) {
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

            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(userId)
                    .action(String.valueOf(ActionLog.CHANGE_PASSWORD))
                    .objectType("user")
                    .typeLog("USER")
                    .status("OK")
                    .errorReason(null)
                    .ip(ip)
                    .userAgent(userAgent)
                    .metadata("{}")
                    .timestamp(Instant.now())
                    .build();

            auditLogProducer.sendAuditLog(logEvent, userId);
        } catch (Exception e) {
            // Ignore audit log errors
            log.warn("Failed to send change password audit log: {}", e.getMessage());
        }
    }

    /**
     * Helper methods để lấy Keycloak config
     */
    @Value("${app.security.keycloak.server-url:http://localhost:9090}")
    private String keycloakServerUrl;

    @Value("${app.security.keycloak.resource-client-id:frontend-app}")
    private String keycloakClientId;

    @Value("${KEYCLOAK_CLIENT_SECRET:}")
    private String keycloakClientSecret;

    private String getKeycloakServerUrl() {
        return keycloakServerUrl;
    }

    private String getKeycloakClientId() {
        return keycloakClientId;
    }

    private String getKeycloakClientSecret() {
        return keycloakClientSecret;
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

    /**
     * Đồng bộ thông tin user từ database lên Keycloak
     * Cập nhật fullName và profile (custom attributes) trong Keycloak
     */
    private void syncUserToKeycloak(User user, UpdateProfileRequest request) {
        if (keycloakAdmin == null) {
            log.warn("Keycloak admin client is not available, skipping Keycloak sync");
            return;
        }

        try {
            RealmResource realm = keycloakAdmin.realm(keycloakRealm);
            UserResource userResource = realm.users().get(user.getId().toString());
            
            // Lấy thông tin user hiện tại từ Keycloak
            UserRepresentation userRep = userResource.toRepresentation();
            boolean needsUpdate = false;

            // Cập nhật fullName (firstName trong Keycloak)
            if (request.getFullName() != null && !request.getFullName().equals(userRep.getFirstName())) {
                userRep.setFirstName(request.getFullName());
                needsUpdate = true;
                log.debug("Updating firstName in Keycloak for user {}: {}", user.getId(), request.getFullName());
            }

            // Cập nhật profile vào custom attributes trong Keycloak
            if (request.getProfile() != null && !request.getProfile().isEmpty()) {
                Map<String, List<String>> attributes = userRep.getAttributes();
                if (attributes == null) {
                    attributes = new HashMap<>();
                }

                // Merge profile vào attributes
                // Lưu profile dưới dạng JSON string trong attribute "profile"
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String profileJson = mapper.writeValueAsString(user.getProfile());
                    attributes.put("profile", List.of(profileJson));
                    userRep.setAttributes(attributes);
                    needsUpdate = true;
                    log.debug("Updating profile attribute in Keycloak for user {}", user.getId());
                } catch (Exception e) {
                    log.warn("Failed to serialize profile to JSON for Keycloak sync: {}", e.getMessage());
                }
            }

            // Chỉ update nếu có thay đổi
            if (needsUpdate) {
                userResource.update(userRep);
                log.info("✅ Successfully synced user profile to Keycloak for user: {}", user.getId());
            } else {
                log.debug("No changes to sync to Keycloak for user: {}", user.getId());
            }

        } catch (jakarta.ws.rs.WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404) {
                log.warn("User {} not found in Keycloak, cannot sync profile", user.getId());
            } else {
                log.error("Keycloak API error while syncing user profile for user {}: {} (status: {})", 
                    user.getId(), e.getMessage(), 
                    e.getResponse() != null ? e.getResponse().getStatus() : "unknown");
            }
        } catch (Exception e) {
            // Log error nhưng không throw exception để không ảnh hưởng đến flow chính
            // Database đã được lưu thành công, Keycloak sync chỉ là bonus
            log.error("Failed to sync user profile to Keycloak for user {}: {}", user.getId(), e.getMessage(), e);
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