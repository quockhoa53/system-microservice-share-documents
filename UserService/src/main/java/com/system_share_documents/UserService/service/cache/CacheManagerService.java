package com.system_share_documents.UserService.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service để quản lý các cache handlers
 * Cho phép dễ dàng thêm handler mới bằng cách inject vào constructor
 */
@Slf4j
@Service
public class CacheManagerService {

    private final Map<String, CacheHandler<?>> handlers = new HashMap<>();

    /**
     * Constructor nhận danh sách các handlers và đăng ký chúng
     */
    public CacheManagerService(List<CacheHandler<?>> cacheHandlers) {
        for (CacheHandler<?> handler : cacheHandlers) {
            String cacheType = handler.getCacheType();
            handlers.put(cacheType, handler);
            log.info("Registered cache handler for type: {}", cacheType);
        }
    }

    /**
     * Lấy handler theo cache type
     */
    @SuppressWarnings("unchecked")
    public <T> CacheHandler<T> getHandler(String cacheType) {
        CacheHandler<?> handler = handlers.get(cacheType);
        if (handler == null) {
            throw new IllegalArgumentException("No cache handler found for type: " + cacheType);
        }
        return (CacheHandler<T>) handler;
    }

    /**
     * Lấy danh sách các cache types đã đăng ký
     */
    public List<String> getAvailableCacheTypes() {
        return handlers.keySet().stream().sorted().collect(java.util.stream.Collectors.toList());
    }

    /**
     * Cache một entity theo type và id
     */
    public int cacheById(String cacheType, UUID id) {
        CacheHandler<?> handler = getHandler(cacheType);
        return handler.cacheById(id);
    }

    /**
     * Cache tất cả entities theo type
     */
    public int cacheAll(String cacheType) {
        CacheHandler<?> handler = getHandler(cacheType);
        return handler.cacheAll();
    }

    /**
     * Xóa cache của một entity
     */
    public void evictCache(String cacheType, UUID id) {
        CacheHandler<?> handler = getHandler(cacheType);
        handler.evictCache(id);
    }

    /**
     * Xóa toàn bộ cache của một type
     */
    public void evictAllCache(String cacheType) {
        CacheHandler<?> handler = getHandler(cacheType);
        handler.evictAllCache();
    }

    /**
     * Kiểm tra cache type có tồn tại không
     */
    public boolean hasCacheType(String cacheType) {
        return handlers.containsKey(cacheType);
    }
}









