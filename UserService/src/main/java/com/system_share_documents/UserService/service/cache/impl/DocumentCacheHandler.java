package com.system_share_documents.UserService.service.cache.impl;

import com.system_share_documents.UserService.service.cache.CacheHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Handler để cache Document data
 * Lưu ý: Document nằm ở DocumentService, handler này chỉ quản lý cache trên Redis
 * Để cache document, cần gọi DocumentService qua internal API hoặc tạo job riêng
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentCacheHandler implements CacheHandler<Map<String, Object>> {

    private static final String CACHE_TYPE = "document";
    private static final String REDIS_KEY_PREFIX = "document:";
    private static final String DOCUMENT_SET_KEY = "documents:all";

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public String getCacheType() {
        return CACHE_TYPE;
    }

    @Override
    public int cacheById(UUID id) {
        // Document data cần lấy từ DocumentService
        // Có thể implement bằng cách gọi internal API hoặc để service khác gọi
        log.warn("cacheById not implemented for document. Document data should be fetched from DocumentService");
        return 0;
    }

    @Override
    public int cacheAll() {
        // Document data cần lấy từ DocumentService
        log.warn("cacheAll not implemented for document. Document data should be fetched from DocumentService");
        return 0;
    }

    /**
     * Cache document data được cung cấp từ bên ngoài (ví dụ: từ DocumentService)
     */
    public int cacheDocumentData(UUID id, Map<String, Object> documentData) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            
            // Convert map values to strings
            Map<String, String> hashData = documentData.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            e -> {
                                Object val = e.getValue();
                                if (val instanceof Map || val instanceof java.util.List) {
                                    try {
                                        com.fasterxml.jackson.databind.ObjectMapper mapper = 
                                                new com.fasterxml.jackson.databind.ObjectMapper();
                                        return mapper.writeValueAsString(val);
                                    } catch (Exception ex) {
                                        log.warn("Failed to serialize field {}: {}", e.getKey(), ex.getMessage());
                                        return String.valueOf(val);
                                    }
                                }
                                return String.valueOf(val);
                            }
                    ));
            
            hashOps.putAll(redisKey, hashData);
            
            // Add to documents set
            redisTemplate.opsForSet().add(DOCUMENT_SET_KEY, id.toString());
            
            log.info("Cached document with id: {}", id);
            return 1;
        } catch (Exception e) {
            log.error("Error caching document with id: {}", id, e);
            return 0;
        }
    }

    @Override
    public void evictCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            redisTemplate.delete(redisKey);
            redisTemplate.opsForSet().remove(DOCUMENT_SET_KEY, id.toString());
            log.info("Evicted cache for document with id: {}", id);
        } catch (Exception e) {
            log.error("Error evicting cache for document with id: {}", id, e);
        }
    }

    @Override
    public void evictAllCache() {
        try {
            // Delete all document keys
            java.util.Set<Object> documentIds = redisTemplate.opsForSet().members(DOCUMENT_SET_KEY);
            if (documentIds != null) {
                for (Object documentId : documentIds) {
                    String redisKey = REDIS_KEY_PREFIX + documentId.toString();
                    redisTemplate.delete(redisKey);
                }
            }
            
            // Delete the set itself
            redisTemplate.delete(DOCUMENT_SET_KEY);
            log.info("Evicted all document cache");
        } catch (Exception e) {
            log.error("Error evicting all document cache", e);
        }
    }

    @Override
    public Map<String, Object> getFromCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            Map<String, String> hashData = hashOps.entries(redisKey);
            
            if (hashData == null || hashData.isEmpty()) {
                return null;
            }
            
            // Convert back to Map<String, Object>
            return hashData.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue
                    ));
        } catch (Exception e) {
            log.error("Error getting document from cache with id: {}", id, e);
            return null;
        }
    }

    @Override
    public boolean existsInCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.error("Error checking cache existence for document with id: {}", id, e);
            return false;
        }
    }
}









