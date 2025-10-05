package com.system_share_documents.APIGatewayService.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DuplicateLogCache {

    private final Map<String, Long> cache = new ConcurrentHashMap<>();
    private final long ttlMs = 2000;

    public boolean isDuplicate(String key) {
        long now = System.currentTimeMillis();
        Long last = cache.get(key);
        if (last != null && (now - last) < ttlMs) {
            return true;
        }
        cache.put(key, now);
        return false;
    }
}

