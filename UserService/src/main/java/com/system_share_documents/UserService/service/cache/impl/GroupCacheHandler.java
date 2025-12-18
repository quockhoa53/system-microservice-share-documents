package com.system_share_documents.UserService.service.cache.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.UserService.entity.Group;
import com.system_share_documents.UserService.repository.GroupRepository;
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
public class GroupCacheHandler implements CacheHandler<Group> {

    private static final String CACHE_TYPE = "group";
    private static final String REDIS_KEY_PREFIX = "group:";
    private static final String GROUP_SET_KEY = "groups:all";

    private final RedisTemplate<String, Object> redisTemplate;
    private final GroupRepository groupRepository;
    @org.springframework.beans.factory.annotation.Qualifier("redisObjectMapper")
    private final ObjectMapper objectMapper;

    @Override
    public String getCacheType() {
        return CACHE_TYPE;
    }

    @Override
    public int cacheById(UUID id) {
        try {
            Group group = groupRepository.findById(id).orElse(null);
            if (group == null) {
                log.warn("Group not found with id: {}", id);
                return 0;
            }

            String redisKey = REDIS_KEY_PREFIX + id;
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            
            // Convert group to map and serialize
            Map<String, String> hashData = convertGroupToHash(group);
            hashOps.putAll(redisKey, hashData);
            
            // Add to groups set
            redisTemplate.opsForSet().add(GROUP_SET_KEY, id.toString());
            
            log.info("Cached group with id: {}", id);
            return 1;
        } catch (Exception e) {
            log.error("Error caching group with id: {}", id, e);
            return 0;
        }
    }

    @Override
    public int cacheAll() {
        try {
            List<Group> groups = groupRepository.findAll();
            int cachedCount = 0;
            
            for (Group group : groups) {
                try {
                    String redisKey = REDIS_KEY_PREFIX + group.getId();
                    HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
                    
                    Map<String, String> hashData = convertGroupToHash(group);
                    hashOps.putAll(redisKey, hashData);
                    
                    // Add to groups set
                    redisTemplate.opsForSet().add(GROUP_SET_KEY, group.getId().toString());
                    cachedCount++;
                } catch (Exception e) {
                    log.error("Error caching group with id: {}", group.getId(), e);
                }
            }
            
            log.info("Cached {} groups", cachedCount);
            return cachedCount;
        } catch (Exception e) {
            log.error("Error caching all groups", e);
            return 0;
        }
    }

    @Override
    public void evictCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            redisTemplate.delete(redisKey);
            redisTemplate.opsForSet().remove(GROUP_SET_KEY, id.toString());
            log.info("Evicted cache for group with id: {}", id);
        } catch (Exception e) {
            log.error("Error evicting cache for group with id: {}", id, e);
        }
    }

    @Override
    public void evictAllCache() {
        try {
            // Delete all group keys
            java.util.Set<Object> members = redisTemplate.opsForSet().members(GROUP_SET_KEY);
            if (members != null) {
                List<String> groupIds = members.stream()
                        .map(Object::toString)
                        .collect(java.util.stream.Collectors.toList());
                
                for (String groupId : groupIds) {
                    String redisKey = REDIS_KEY_PREFIX + groupId;
                    redisTemplate.delete(redisKey);
                }
            }
            
            // Delete the set itself
            redisTemplate.delete(GROUP_SET_KEY);
            log.info("Evicted all group cache");
        } catch (Exception e) {
            log.error("Error evicting all group cache", e);
        }
    }

    @Override
    public Group getFromCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();
            Map<String, String> hashData = hashOps.entries(redisKey);
            
            if (hashData == null || hashData.isEmpty()) {
                return null;
            }
            
            return convertHashToGroup(hashData);
        } catch (Exception e) {
            log.error("Error getting group from cache with id: {}", id, e);
            return null;
        }
    }

    @Override
    public boolean existsInCache(UUID id) {
        try {
            String redisKey = REDIS_KEY_PREFIX + id;
            return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        } catch (Exception e) {
            log.error("Error checking cache existence for group with id: {}", id, e);
            return false;
        }
    }

    private Map<String, String> convertGroupToHash(Group group) {
        try {
            // Convert to map using ObjectMapper
            Map<String, Object> groupMap = objectMapper.convertValue(group, Map.class);
            
            // Handle owner relationship - store owner ID as string if present
            if (group.getOwner() != null) {
                groupMap.put("owner_id", group.getOwner().getId().toString());
            }
            
            // Convert all values to strings
            return groupMap.entrySet().stream()
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
            log.error("Error converting group to hash", e);
            throw new RuntimeException("Failed to convert group to hash", e);
        }
    }

    private Group convertHashToGroup(Map<String, String> hashData) {
        try {
            // Convert hash map to Group object
            Map<String, Object> groupMap = hashData.entrySet().stream()
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
            
            return objectMapper.convertValue(groupMap, Group.class);
        } catch (Exception e) {
            log.error("Error converting hash to group", e);
            return null;
        }
    }
}










