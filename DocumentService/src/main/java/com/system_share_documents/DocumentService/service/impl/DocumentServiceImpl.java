package com.system_share_documents.DocumentService.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.cache.document.DocumentCacheService;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.DocumentService.dto.request.DeleteDocumentRequest;
import com.system_share_documents.DocumentService.dto.response.DeleteDocumentResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.dto.response.SharedDocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.AuthError;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.DocumentService;
import com.system_share_documents.DocumentService.utils.MapperUtils;
import jakarta.servlet.http.HttpServletRequest;
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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;
import static com.system_share_documents.AppCommonService.utils.ProcessJsonUtils.convertJson;

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
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private AuditLogProducer auditLogProducer;

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
            // Tối ưu: Paginate trước để tránh load tất cả documents vào memory
            int totalCached = cachedDocs.size();
            int start = page * size;
            int end = Math.min(start + size, totalCached);

            // Lấy page data từ cache
            List<DocumentCacheResponse> pageCachedDocs = start < totalCached
                    ? cachedDocs.subList(start, end)
                    : new ArrayList<>();

            // Map cached documents to response (không cần query DB vì cache đã được sync bởi removeDocumentFromCache và CDC job)
            for (DocumentCacheResponse c : pageCachedDocs) {
                results.add(mapperUtils.mapDocumentCacheToResponse(c));
            }

            // Nếu chưa đủ size và có thể có documents chưa được cache
            if (results.size() < size && end >= totalCached) {
                Set<String> docIds = documentCacheService.getDocumentIdsOfUser(userId);
                Set<String> cachedDocIds = cachedDocs.stream()
                        .map(DocumentCacheResponse::getId)
                        .collect(Collectors.toSet());

                List<String> missingIds = docIds.stream()
                        .filter(id -> !cachedDocIds.contains(id))
                        .limit(size - results.size()) // Chỉ lấy số lượng cần thiết
                        .toList();

                if (!missingIds.isEmpty()) {
                    List<UUID> uuidMissing = missingIds.stream()
                            .map(UUID::fromString)
                            .toList();
                    List<Document> entities = documentRepository.findAllByIdIn(uuidMissing).stream()
                            .filter(d -> d.getDeletedAt() == null)
                            .limit(size - results.size())
                            .toList();

                    for (Document e : entities) {
                        results.add(mapperUtils.mapDocumentEntityToResponse(e));
                    }
                }

                // Total count dựa trên số lượng IDs trong cache set
                // (deleted documents đã được xóa khỏi cache bởi removeDocumentFromCache và CDC job)
                return new PageImpl<>(results, pageable, docIds != null ? docIds.size() : totalCached);
            }

            // Return với total từ cache size
            return new PageImpl<>(results, pageable, totalCached);
        }

        // Fallback: Query từ database nếu không có cache
        Page<Document> entityPage = documentRepository.findActiveDocumentsByOwnerId(userId, pageable);
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

    @Override
    public DeleteDocumentResponse deleteDocument(DeleteDocumentRequest request, String userId, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        Document doc = null;
        long deletedVersionsCount = 0;

        try {
            doc = documentRepository.findByIdAndNotDeleted(request.getDocumentId())
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            if (!doc.getOwnerId().equals(userId)) {
                throw new AppException(AuthError.FORBIDDEN_ACTION_DELETE);
            }

            deletedVersionsCount = documentVersionRepository.countByDocumentIdAndDeletedAtIsNull(doc.getId());

            // Soft delete tất cả versions bằng batch update (tối ưu cho 1M records)
            Timestamp now = Timestamp.from(Instant.now());
            documentVersionRepository.batchSoftDeleteByDocumentId(doc.getId(), now);

            // Soft delete document
            doc.setDeletedAt(now);
            doc.setUpdatedAt(now);
            documentRepository.save(doc);

            try {
                documentCacheService.removeDocumentFromCache(doc.getId().toString(), doc.getOwnerId());
            } catch (Exception cacheException) {
                // Log lỗi nhưng không throw để không ảnh hưởng đến kết quả xóa document
                // Cache sẽ được đồng bộ lại bởi CDC job sau đó
                // Có thể thêm logger ở đây nếu cần
            }

            return DeleteDocumentResponse.builder()
                    .documentId(doc.getId())
                    .deleted(true)
                    .deletedVersionsCount(deletedVersionsCount)
                    .build();

        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_DELETE_DOCUMENT, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(userId)
                        .action(String.valueOf(ActionLog.DELETE_DOCUMENT))
                        .documentId(doc.getId().toString())
                        .objectType(DOCUMENT)
                        .status(status)
                        .errorReason(errorReason)
                        .ip(getClientIp(httpRequest))
                        .userAgent(getUserAgent(httpRequest))
                        .metadata(null)
                        .request(convertJson(request))
                        .build();

                auditLogProducer.sendAuditLog(logEvent, doc.getId().toString());
            }
        }
    }
}
