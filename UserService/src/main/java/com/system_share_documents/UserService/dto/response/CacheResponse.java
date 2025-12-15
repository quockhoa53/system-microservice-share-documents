package com.system_share_documents.UserService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO cho cache operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheResponse {
    private String cacheType;
    private int cachedCount;
    private String message;
    private List<String> availableCacheTypes;
}



