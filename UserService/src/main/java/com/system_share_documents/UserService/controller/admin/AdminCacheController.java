package com.system_share_documents.UserService.controller.admin;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.CacheRequest;
import com.system_share_documents.UserService.dto.response.CacheResponse;
import com.system_share_documents.UserService.service.cache.CacheManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller để admin quản lý cache trên Redis
 * Cho phép cache các loại dữ liệu: user, group, document, ...
 */
@Slf4j
@RestController
@RequestMapping("/admin/cache")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCacheController {

    private final CacheManagerService cacheManagerService;

    /**
     * Lấy danh sách các loại cache có sẵn
     * GET /admin/cache/types
     */
    @GetMapping("/types")
    public ApiResponse<CacheResponse> getAvailableCacheTypes() {
        log.info("Admin: Get available cache types");
        
        var availableTypes = cacheManagerService.getAvailableCacheTypes();
        CacheResponse response = CacheResponse.builder()
                .availableCacheTypes(availableTypes)
                .message("Available cache types retrieved successfully")
                .build();
        
        return ApiResponse.success("OK", response.getMessage(), response);
    }

    /**
     * Cache một entity cụ thể theo ID
     * POST /admin/cache
     * Body: { "cacheType": "user", "id": "uuid-here" }
     */
    @PostMapping
    public ApiResponse<CacheResponse> cacheById(@RequestBody CacheRequest request) {
        log.info("Admin: Cache entity - type={}, id={}", request.getCacheType(), request.getId());
        
        validateCacheRequest(request);
        
        int cachedCount = cacheManagerService.cacheById(request.getCacheType(), request.getId());
        
        CacheResponse response = CacheResponse.builder()
                .cacheType(request.getCacheType())
                .cachedCount(cachedCount)
                .message(String.format("Cached %d entity(ies) of type %s", cachedCount, request.getCacheType()))
                .build();
        
        return ApiResponse.success("OK", response.getMessage(), response);
    }

    /**
     * Cache một entity cụ thể theo type và id (URL path)
     * POST /admin/cache/{cacheType}/{id}
     */
    @PostMapping("/{cacheType}/{id}")
    public ApiResponse<CacheResponse> cacheById(
            @PathVariable String cacheType,
            @PathVariable UUID id
    ) {
        log.info("Admin: Cache entity - type={}, id={}", cacheType, id);
        
        validateCacheType(cacheType);
        
        int cachedCount = cacheManagerService.cacheById(cacheType, id);
        
        CacheResponse response = CacheResponse.builder()
                .cacheType(cacheType)
                .cachedCount(cachedCount)
                .message(String.format("Cached %d entity(ies) of type %s", cachedCount, cacheType))
                .build();
        
        return ApiResponse.success("OK", response.getMessage(), response);
    }

    /**
     * Cache tất cả entities của một type
     * POST /admin/cache/{cacheType}/all
     */
    @PostMapping("/{cacheType}/all")
    public ApiResponse<CacheResponse> cacheAll(@PathVariable String cacheType) {
        log.info("Admin: Cache all entities - type={}", cacheType);
        
        validateCacheType(cacheType);
        
        int cachedCount = cacheManagerService.cacheAll(cacheType);
        
        CacheResponse response = CacheResponse.builder()
                .cacheType(cacheType)
                .cachedCount(cachedCount)
                .message(String.format("Cached %d entity(ies) of type %s", cachedCount, cacheType))
                .build();
        
        return ApiResponse.success("OK", response.getMessage(), response);
    }

    /**
     * Xóa cache của một entity cụ thể
     * DELETE /admin/cache/{cacheType}/{id}
     */
    @DeleteMapping("/{cacheType}/{id}")
    public ApiResponse<CacheResponse> evictCache(
            @PathVariable String cacheType,
            @PathVariable UUID id
    ) {
        log.info("Admin: Evict cache - type={}, id={}", cacheType, id);
        
        validateCacheType(cacheType);
        
        cacheManagerService.evictCache(cacheType, id);
        
        CacheResponse response = CacheResponse.builder()
                .cacheType(cacheType)
                .cachedCount(0)
                .message(String.format("Evicted cache for %s with id %s", cacheType, id))
                .build();
        
        return ApiResponse.success("OK", response.getMessage(), response);
    }

    /**
     * Xóa toàn bộ cache của một type
     * DELETE /admin/cache/{cacheType}
     */
    @DeleteMapping("/{cacheType}")
    public ApiResponse<CacheResponse> evictAllCache(@PathVariable String cacheType) {
        log.info("Admin: Evict all cache - type={}", cacheType);
        
        validateCacheType(cacheType);
        
        cacheManagerService.evictAllCache(cacheType);
        
        CacheResponse response = CacheResponse.builder()
                .cacheType(cacheType)
                .cachedCount(0)
                .message(String.format("Evicted all cache for type %s", cacheType))
                .build();
        
        return ApiResponse.success("OK", response.getMessage(), response);
    }

    /**
     * Xóa toàn bộ cache của tất cả types
     * DELETE /admin/cache
     */
    @DeleteMapping
    public ApiResponse<CacheResponse> evictAllCaches() {
        log.info("Admin: Evict all caches");
        
        var cacheTypes = cacheManagerService.getAvailableCacheTypes();
        int totalEvicted = 0;
        
        for (String cacheType : cacheTypes) {
            cacheManagerService.evictAllCache(cacheType);
            totalEvicted++;
        }
        
        CacheResponse response = CacheResponse.builder()
                .cachedCount(totalEvicted)
                .message(String.format("Evicted all caches for %d type(s)", totalEvicted))
                .build();
        
        return ApiResponse.success("OK", response.getMessage(), response);
    }

    private void validateCacheRequest(CacheRequest request) {
        if (request.getCacheType() == null || request.getCacheType().trim().isEmpty()) {
            throw new IllegalArgumentException("cacheType is required");
        }
        if (request.getId() == null) {
            throw new IllegalArgumentException("id is required for cacheById operation");
        }
        validateCacheType(request.getCacheType());
    }

    private void validateCacheType(String cacheType) {
        if (!cacheManagerService.hasCacheType(cacheType)) {
            throw new IllegalArgumentException(
                    String.format("Invalid cache type: %s. Available types: %s", 
                            cacheType, 
                            cacheManagerService.getAvailableCacheTypes())
            );
        }
    }
}




