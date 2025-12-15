package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final CacheManager cacheManager;

    @Override
    public void evictUserCache(UUID userId) {
        if (cacheManager.getCache("users") != null) {
            cacheManager.getCache("users").evict(userId.toString());
            log.debug("Evicted user cache for userId: {}", userId);
        }
    }

    @Override
    public void evictUserCacheByUsername(String username) {
        if (cacheManager.getCache("users") != null) {
            // Note: This evicts by username key if you cache by username
            cacheManager.getCache("users").evict("username:" + username);
            log.debug("Evicted user cache for username: {}", username);
        }
    }

    @Override
    public void evictAllUserCache() {
        if (cacheManager.getCache("users") != null) {
            cacheManager.getCache("users").clear();
            log.debug("Evicted all user cache");
        }
    }

    @Override
    public void evictGroupCache(UUID groupId) {
        if (cacheManager.getCache("groups") != null) {
            cacheManager.getCache("groups").evict(groupId.toString());
            log.debug("Evicted group cache for groupId: {}", groupId);
        }
    }

    @Override
    public void evictGroupMemberCache(UUID groupId) {
        if (cacheManager.getCache("groupMembers") != null) {
            cacheManager.getCache("groupMembers").evict(groupId.toString());
            log.debug("Evicted group member cache for groupId: {}", groupId);
        }
        // Also evict user groups cache since membership changed
        evictAllUserGroupsCache();
    }

    @Override
    public void evictUserGroupsCache(UUID userId) {
        if (cacheManager.getCache("userGroups") != null) {
            cacheManager.getCache("userGroups").evict(userId.toString());
            log.debug("Evicted user groups cache for userId: {}", userId);
        }
    }

    @Override
    public void evictAllGroupCache() {
        if (cacheManager.getCache("groups") != null) {
            cacheManager.getCache("groups").clear();
        }
        if (cacheManager.getCache("groupMembers") != null) {
            cacheManager.getCache("groupMembers").clear();
        }
        if (cacheManager.getCache("userGroups") != null) {
            cacheManager.getCache("userGroups").clear();
        }
        log.debug("Evicted all group cache");
    }

    @Override
    public void evictAllCache() {
        evictAllUserCache();
        evictAllGroupCache();
        log.debug("Evicted all cache");
    }

    private void evictAllUserGroupsCache() {
        if (cacheManager.getCache("userGroups") != null) {
            cacheManager.getCache("userGroups").clear();
        }
    }
}


