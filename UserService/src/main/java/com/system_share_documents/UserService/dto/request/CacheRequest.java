package com.system_share_documents.UserService.dto.request;

import lombok.Data;

import java.util.UUID;

/**
 * Request DTO cho cache operations
 */
@Data
public class CacheRequest {
    /**
     * Loại cache (user, group, document, ...)
     */
    private String cacheType;
    
    /**
     * ID của entity cần cache (null nếu muốn cache tất cả)
     */
    private UUID id;
}

