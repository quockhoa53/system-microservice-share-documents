package com.system_share_documents.UserService.service.cache.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.cache.CacheHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserCacheHandler implements CacheHandler<User> {

    private static final String CACHE_TYPE = "user";
    private static final String REDIS_KEY_PREFIX = "user:";
    private static final String USER_SET_KEY = "users:all";

    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;
    @org.springframework.beans.factory.annotation.Qualifier("redisObjectMapper")
    private final ObjectMapper objectMapper;

    @Override
    public String getCacheType() {
        return CACHE_TYPE;
    }

    @Override
    public int cacheById(UUID id) {
        try {
            User user = userRepository.findById(id).orElse(null);
            if (user == null) {
                log.warn("User not found with id: {}", id);
                return 0;
            }

            String redisKey = REDIS_KEY_PREFIX + id;
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            
            // Convert user to map and serialize
            Map<String, String> hashData = convertUserToHash(user);
            hashOps.putAll(redisKey, hashData);
            
            // Add to users set
            redisTemplate.opsForSet().add(USER_SET_KEY, id.toString());
            
            log.info("Cached user with id: {}", id);
            return 1;
        } catch (Exception e) {
            log.error("Error caching user with id: {}", id, e);
            return 0;
        }
    }

    @Override
    public int cacheAll() {
        try {
            List<User> users = userRepository.findAll();
            int cachedCount = 0;
            
            for (User user : users) {
                try {
                    String redisKey = REDIS_KEY_PREFIX + user.getId();
                    HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
                    
                    Map<String, String> hashData = convertUserToHash(user);
                    hashOps.putAll(redisKey, hashData);
                    
                    // Add to users set
                    redisTemplate.opsForSet().add(USER_SET_KEY, user.getId().toString());
                    cachedCount++;
                } catch (Exception e) {
                    log.error("Error caching user with id: {}", user.getId(), e);
                }
            }
            
            log.info("Cached {} users", cachedCount);
            return cachedCount;
        } catch (Exception e) {
            log.error("Error caching all users", e);
            return 0;
        }
    }

    @Override
    public void evictCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            redisTemplate.delete(redisKey);
            redisTemplate.opsForSet().remove(USER_SET_KEY, id.toString());
            log.info("Evicted cache for user with id: {}", id);
        } catch (Exception e) {
            log.error("Error evicting cache for user with id: {}", id, e);
        }
    }

    @Override
    public void evictAllCache() {
        try {
            // Delete all user keys
            java.util.Set<Object> members = redisTemplate.opsForSet().members(USER_SET_KEY);
            if (members != null) {
                List<String> userIds = members.stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.toList());
            
                for (String userId : userIds) {
                    String redisKey = REDIS_KEY_PREFIX + userId;
                    redisTemplate.delete(redisKey);
                }
            }
            
            // Delete the set itself
            redisTemplate.delete(USER_SET_KEY);
            log.info("Evicted all user cache");
        } catch (Exception e) {
            log.error("Error evicting all user cache", e);
        }
    }

    @Override
    public User getFromCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            Map<String, String> hashData = hashOps.entries(redisKey);
            
            if (hashData == null || hashData.isEmpty()) {
                return null;
            }
            
            return convertHashToUser(hashData);
        } catch (Exception e) {
            log.error("Error getting user from cache with id: {}", id, e);
            return null;
        }
    }

    @Override
    public boolean existsInCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.error("Error checking cache existence for user with id: {}", id, e);
            return false;
        }
    }

    private Map<String, String> convertUserToHash(User user) {
        try {
            // Convert to map using ObjectMapper
            Map<String, Object> userMap = objectMapper.convertValue(user, Map.class);
            
            // Convert all values to strings
            return userMap.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            e -> {
                                Object val = e.getValue();
                                try {
                                    if (val instanceof Map || val instanceof List) {
                                        return objectMapper.writeValueAsString(val);
                                    }
                                } catch (Exception ex) {
                                    log.warn("Failed to serialize field {}: {}", e.getKey(), ex.getMessage());
                                }
                                return String.valueOf(val);
                            }
                    ));
        } catch (Exception e) {
            log.error("Error converting user to hash", e);
            throw new RuntimeException("Failed to convert user to hash", e);
        }
    }

    private User convertHashToUser(Map<String, String> hashData) {
        try {
            // Convert hash map to User object
            Map<String, Object> userMap = hashData.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            e -> {
                                String val = e.getValue();
                                // Try to parse as JSON if it looks like JSON
                                if (val.startsWith("{") || val.startsWith("[")) {
                                    try {
                                        return objectMapper.readValue(val, Object.class);
                                    } catch (Exception ex) {
                                        // If parsing fails, return as string
                                        return val;
                                    }
                                }
                                return val;
                            }
                    ));
            
            return objectMapper.convertValue(userMap, User.class);
        } catch (Exception e) {
            log.error("Error converting hash to user", e);
            return null;
        }
    }
}










