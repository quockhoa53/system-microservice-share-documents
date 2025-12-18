package com.system_share_documents.AppCommonService.cache.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.dto.response.UserCacheResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.system_share_documents.AppCommonService.constant.PrefixCacheConstant.PREFIX_USER_KEY;

/**
 * Implementation của UserCacheService
 * Đọc user cache từ Redis (shared cache)
 * Tất cả services có thể dùng service này để đọc user cache từ shared Redis
 */
@Service
public class UserCacheServiceImpl implements UserCacheService {

    private static final Logger log = LoggerFactory.getLogger(UserCacheServiceImpl.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public UserCacheResponse getUserFromCache(String userId) {
        if (userId == null || userId.isEmpty()) {
            log.warn("getUserFromCache called with null or empty userId");
            return null;
        }

        String key = PREFIX_USER_KEY + userId;
        try {
            // Lấy tất cả field từ hash
            var hashMap = redisTemplate.opsForHash().entries(key);
            if (hashMap == null || hashMap.isEmpty()) {
                log.debug("Cache miss for userId: {}", userId);
                return null;
            }

            // Chuyển Map<String, String> sang JSON -> parse object
            String json = objectMapper.writeValueAsString(hashMap);
            UserCacheResponse result = objectMapper.readValue(json, UserCacheResponse.class);

            log.debug("Cache hit for userId: {}", userId);
            return result;
        } catch (Exception e) {
            // Log error nhưng return null thay vì throw để service có thể fallback về UserService API
            log.error("Failed to parse Redis HASH for key: {}. Error: {}", key, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<UserCacheResponse> getUsersFromCache(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        List<UserCacheResponse> result = new ArrayList<>(userIds.size());

        try {
            // Tối ưu: Dùng Redis Pipeline để batch tất cả HGETALL operations
            @SuppressWarnings("unchecked")
            List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String userId : userIds) {
                    String key = PREFIX_USER_KEY + userId;
                    connection.hGetAll(key.getBytes());
                }
                return null;
            });

            // Parse results từ pipeline
            int parseErrors = 0;
            for (int i = 0; i < pipelineResults.size(); i++) {
                @SuppressWarnings("unchecked")
                Map<byte[], byte[]> hashData = (Map<byte[], byte[]>) pipelineResults.get(i);
                if (hashData != null && !hashData.isEmpty()) {
                    try {
                        // Convert byte[] keys và values sang String
                        Map<String, String> stringMap = new java.util.HashMap<>();
                        for (Map.Entry<byte[], byte[]> entry : hashData.entrySet()) {
                            stringMap.put(new String(entry.getKey(), StandardCharsets.UTF_8),
                                    new String(entry.getValue(), StandardCharsets.UTF_8));
                        }

                        // Parse JSON
                        String json = objectMapper.writeValueAsString(stringMap);
                        UserCacheResponse user = objectMapper.readValue(json, UserCacheResponse.class);
                        if (user != null) {
                            result.add(user);
                        }
                    } catch (Exception e) {
                        parseErrors++;
                        log.warn("Failed to parse user from cache. userId: {}. Error: {}",
                                userIds.get(i), e.getMessage());
                    }
                }
            }

            if (parseErrors > 0) {
                log.warn("Failed to parse {} out of {} users from cache", parseErrors, userIds.size());
            }

            log.debug("Retrieved {} users from cache out of {} requested", result.size(), userIds.size());
        } catch (Exception e) {
            log.error("Failed to execute Redis pipeline for users. Error: {}", e.getMessage(), e);
            return List.of();
        }

        return result;
    }
}















