package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.cache.document.DocumentCacheService;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.service.DocumentService;
import com.system_share_documents.DocumentService.utils.MapperUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    @Autowired
    private DocumentCacheService documentCacheService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private MapperUtils mapperUtils;

    @Override
    public Page<DocumentResponse> getListDocumentOfUser(String userId, int page, int size) throws Exception {
        if (userId == null || userId.isEmpty()) {
            return Page.empty();
        }
        Pageable pageable = PageRequest.of(page, size);
        List<DocumentCacheResponse> cachedDocs = documentCacheService.getDocumentsOfUser(userId);
        List<DocumentResponse> results = new ArrayList<>();
        if (cachedDocs != null && !cachedDocs.isEmpty()) {
            int start = Math.min(page * size, cachedDocs.size());
            int end = Math.min(start + size, cachedDocs.size());
            List<DocumentCacheResponse> pageCache = cachedDocs.subList(start, end);
            for (DocumentCacheResponse c : pageCache) {
                results.add(mapperUtils.mapDocumentCacheToResponse(c));
            }
            Set<String> docIds = null;
            if (end < size) {
                Set<String> cachedIds = cachedDocs.stream()
                        .map(DocumentCacheResponse::getId)
                        .collect(Collectors.toSet());
                docIds = documentCacheService.getDocumentIdsOfUser(userId);
                List<String> missingIds = docIds.stream()
                        .filter(id -> !cachedIds.contains(id))
                        .toList();
                if (!missingIds.isEmpty()) {
                    List<UUID> uuidMissing = missingIds.stream().map(UUID::fromString).toList();
                    List<Document> entities = documentRepository.findAllByIdIn(uuidMissing);
                    for (Document e : entities) {
                        results.add(mapperUtils.mapDocumentEntityToResponse(e));
                        if (results.size() >= size) break; // đảm bảo trả đúng size
                    }
                }
            }

            if (docIds != null && !docIds.isEmpty()) {
                return new PageImpl<>(results, pageable, docIds.size());
            }
        }

        Page<Document> entityPage = documentRepository.findAllByOwnerId(userId, pageable);
        for (Document e : entityPage.getContent()) {
            results.add(mapperUtils.mapDocumentEntityToResponse(e));
        }

        return new PageImpl<>(results, pageable, entityPage.getTotalElements());
    }

}
