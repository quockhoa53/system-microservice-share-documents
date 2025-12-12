package com.system_share_documents.DocumentService.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.cache.document.DocumentCacheService;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.dto.response.SharedDocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.service.DocumentService;
import com.system_share_documents.DocumentService.utils.MapperUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final String DOCUMENTS_INDEX = "documents";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    @Autowired
    private RestHighLevelClient elasticsearchClient;

    @Autowired
    private DocumentCacheService documentCacheService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentKeyRepository documentKeyRepository;

    @Autowired
    private MapperUtils mapperUtils;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Override
    public List<DocumentResponse> searchDocuments(String query, Integer limit) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        int searchLimit = limit != null ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        String normalizedQuery = query.trim().toLowerCase();

        try {
            SearchRequest searchRequest = new SearchRequest(DOCUMENTS_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            // Sử dụng multi-match query với prefix fields cho real-time search
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                    .should(QueryBuilders.multiMatchQuery(normalizedQuery)
                            .field("original_filename.prefix", 2.0f)  // Boost filename matches
                            .field("original_filename", 1.0f)
                            .type(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
                            .fuzziness("AUTO"))
                    .should(QueryBuilders.wildcardQuery("original_filename.keyword", "*" + normalizedQuery + "*"))
                    .minimumShouldMatch(1);

            // Chỉ lấy documents chưa bị xóa (deleted_at IS NULL)
            boolQuery.mustNot(QueryBuilders.existsQuery("deleted_at"));

            searchSourceBuilder.query(boolQuery);
            searchSourceBuilder.size(searchLimit);
            searchSourceBuilder.fetchSource(true);

            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);

            List<DocumentResponse> documents = new ArrayList<>();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                Map<String, Object> sourceMap = hit.getSourceAsMap();
                DocumentResponse document = mapperUtils.mapToDocumentResponse(sourceMap);
                if (document != null) {
                    documents.add(document);
                }
            }
            return documents;

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public Page<SharedDocumentResponse> getSharedDocuments(String userId, int page, int size) throws JsonProcessingException {
        Pageable pageable = PageRequest.of(page, size);
        int limit = size + 1;
        Timestamp createdAt = null;
        UUID docId = null;
        String jsonResult = documentRepository.getSharedDocuments(userId, limit, createdAt, docId);

        if (jsonResult == null || jsonResult.trim().isEmpty() || jsonResult.equals("null")) {
            return Page.empty(pageable);
        }

        List<SharedDocumentResponse> sharedDocs = objectMapper.readValue(
                jsonResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, SharedDocumentResponse.class)
        );

        if (sharedDocs == null || sharedDocs.isEmpty()) {
            return Page.empty(pageable);
        }
        boolean hasNext = sharedDocs.size() > size;
        if (hasNext) {
            sharedDocs = sharedDocs.subList(0, size);
        }
        long total = hasNext ? (page + 1) * size + 1 : (page * size) + sharedDocs.size();

        return new PageImpl<>(sharedDocs, pageable, total);
    }
}
