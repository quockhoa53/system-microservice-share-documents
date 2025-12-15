package com.system_share_documents.UserService.service;

import java.util.UUID;

/**
 * Service để quản lý cache cho User và Group
 */
public interface CacheService {
    
    // User cache operations
    void evictUserCache(UUID userId);
    void evictUserCacheByUsername(String username);
    void evictAllUserCache();
    
    // Group cache operations
    void evictGroupCache(UUID groupId);
    void evictGroupMemberCache(UUID groupId);
    void evictUserGroupsCache(UUID userId);
    void evictAllGroupCache();
    
    // Combined eviction
    void evictAllCache();
}


