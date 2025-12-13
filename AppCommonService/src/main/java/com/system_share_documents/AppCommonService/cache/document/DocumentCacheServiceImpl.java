package com.system_share_documents.AppCommonService.cache.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.system_share_documents.AppCommonService.constant.PrefixCacheConstant.PREFIX_DOCUMENT_KEY;
import static com.system_share_documents.AppCommonService.constant.PrefixCacheConstant.PREFIX_USER_DOCUMENT_KEY;

@Service
public class DocumentCacheServiceImpl implements DocumentCacheService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public DocumentCacheResponse getDocumentFromCache(String documentId) {
        String key = PREFIX_DOCUMENT_KEY + documentId;
        var hashMap = redisTemplate.opsForHash().entries(key);
        if (hashMap == null || hashMap.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(hashMap);
            return objectMapper.readValue(json, DocumentCacheResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Cannot parse Redis HASH for key: " + key, e);
        }
    }

    @Override
    public List<DocumentCacheResponse> getDocumentsOfUser(String userId) {
        if (userId == null || userId.isEmpty()) {
            return List.of();
        }

        String userSetKey = PREFIX_USER_DOCUMENT_KEY + userId;
        Set<String> docIds = redisTemplate.opsForSet().members(userSetKey);
        if (docIds == null || docIds.isEmpty()) {
            return List.of();
        }
        List<DocumentCacheResponse> result = new ArrayList<>(docIds.size());
        @SuppressWarnings("unchecked")
        List<Object> pipelineResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String id : docIds) {
                String key = PREFIX_DOCUMENT_KEY + id;
                connection.hGetAll(key.getBytes());
            }
            return null;
        });
        for (int i = 0; i < pipelineResults.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<byte[], byte[]> hashData = (Map<byte[], byte[]>) pipelineResults.get(i);
            if (hashData != null && !hashData.isEmpty()) {
                try {
                    Map<String, String> stringMap = new java.util.HashMap<>();
                    for (Map.Entry<byte[], byte[]> entry : hashData.entrySet()) {
                        stringMap.put(new String(entry.getKey()), new String(entry.getValue()));
                    }
                    String json = objectMapper.writeValueAsString(stringMap);
                    DocumentCacheResponse doc = objectMapper.readValue(json, DocumentCacheResponse.class);
                    if (doc != null) {
                        result.add(doc);
                    }
                } catch (Exception e) {
                }
            }
        }

        return result;
    }

    @Override
    public Set<String> getDocumentIdsOfUser(String userId) {
        String key = PREFIX_USER_DOCUMENT_KEY + userId;
        return redisTemplate.opsForSet().members(key);
    }

    @Override
    public void removeDocumentFromCache(String documentId, String userId) {
        try {
            String documentKey = PREFIX_DOCUMENT_KEY + documentId;
            redisTemplate.delete(documentKey);
            if (userId != null && !userId.isEmpty()) {
                String userSetKey = PREFIX_USER_DOCUMENT_KEY + userId;
                redisTemplate.opsForSet().remove(userSetKey, documentId);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove document from cache: " + documentId, e);
        }
    }
}

