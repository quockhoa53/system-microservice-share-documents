package com.system_share_documents.AppCommonService.cache.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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
        // Lấy tất cả field từ hash
        var hashMap = redisTemplate.opsForHash().entries(key);
        if (hashMap == null || hashMap.isEmpty()) {
            return null;
        }

        try {
            // Chuyển Map<String, String> sang JSON -> parse object
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

        List<DocumentCacheResponse> result = new ArrayList<>();
        for (String id : docIds) {
            DocumentCacheResponse doc = getDocumentFromCache(id);
            if (doc != null) {
                result.add(doc);
            }
        }
        return result;
    }

    @Override
    public Set<String> getDocumentIdsOfUser(String userId) {
        String key = PREFIX_USER_DOCUMENT_KEY + userId;
        return redisTemplate.opsForSet().members(key);
    }
}

