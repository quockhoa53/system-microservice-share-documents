package com.system_share_documents.AppCommonService.cache.document;

import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;

import java.util.List;
import java.util.Set;

public interface DocumentCacheService {
    DocumentCacheResponse getDocumentFromCache(String documentId);
    List<DocumentCacheResponse> getDocumentsOfUser(String userId);
    Set<String> getDocumentIdsOfUser(String userId);
}
