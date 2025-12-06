package com.system_share_documents.AppCommonService.utils;

import com.system_share_documents.AppCommonService.cache.document.DocumentCacheService;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import org.springframework.stereotype.Component;

@Component
public class DocumentUtils {

    private static DocumentCacheService documentCacheService;

    public DocumentUtils(DocumentCacheService documentCacheService) {
        DocumentUtils.documentCacheService = documentCacheService;
    }

    public static Boolean checkExistDocument(String documentId) {
        DocumentCacheResponse doc = documentCacheService.getDocumentFromCache(documentId);
        return doc != null;
    }

    public static boolean isDocumentOwnedByUser(String userId, String documentId) {
        DocumentCacheResponse doc = documentCacheService.getDocumentFromCache(documentId);
        if (doc == null) {
            return false;
        }
        return userId.equals(doc.getOwner_id());
    }
}
