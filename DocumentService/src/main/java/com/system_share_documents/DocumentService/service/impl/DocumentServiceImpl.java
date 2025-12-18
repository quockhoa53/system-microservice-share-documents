package com.system_share_documents.DocumentService.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.cache.document.DocumentCacheService;
import com.system_share_documents.AppCommonService.dto.request.CheckAccessRequest;
import com.system_share_documents.AppCommonService.dto.response.DocumentCacheResponse;
import com.system_share_documents.AppCommonService.dto.response.UserCacheResponse;
import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.AppCommonService.rest.grantAccess.GrantAccessRest;
import com.system_share_documents.AppCommonService.service.EmailService;
import com.system_share_documents.DocumentService.dto.request.DeleteDocumentRequest;
import com.system_share_documents.DocumentService.dto.request.RequestAccessRequest;
import com.system_share_documents.DocumentService.dto.response.DeleteDocumentResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.dto.response.RequestAccessResponse;
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

import static com.system_share_documents.AppCommonService.constant.KeysResponseUtils.CAN_DOWNLOAD;
import static com.system_share_documents.AppCommonService.constant.KeysResponseUtils.DATA;
import static com.system_share_documents.AppCommonService.constant.ObjectTypeConstant.DOCUMENT;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;
import static com.system_share_documents.AppCommonService.utils.ProcessJsonUtils.convertJson;
import static com.system_share_documents.AppCommonService.utils.UserCacheUtils.getUserEmail;
import static com.system_share_documents.AppCommonService.utils.UserCacheUtils.getUserInfo;

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

    @Autowired
    private GrantAccessRest grantAccessRest;

    @Autowired
    private EmailService emailService;

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
    public List<DocumentResponse> searchDocuments(String query, Integer limit, String userIdForFilter, String userIdForPermissions) {
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

            // Nếu có userIdForFilter, filter chỉ lấy documents của user đó (realtime search cho user)
            if (userIdForFilter != null && !userIdForFilter.trim().isEmpty()) {
                boolQuery.must(QueryBuilders.termQuery("owner_id", userIdForFilter));
            }

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
                    // Luôn enrich permissions nếu có userIdForPermissions (user đang đăng nhập)
                    if (userIdForPermissions != null && !userIdForPermissions.trim().isEmpty()) {
                        enrichDocumentWithPermissions(document, userIdForPermissions);
                    }
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

    @Override
    public RequestAccessResponse requestAccess(RequestAccessRequest request, String userId, HttpServletRequest httpRequest) throws Exception {
        String status = "OK";
        String errorReason = null;
        Document doc = null;

        try {
            // Validate document exists
            doc = documentRepository.findByIdAndNotDeleted(UUID.fromString(request.getDocumentId()))
                    .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

            // Check if user is already the owner
            if (doc.getOwnerId().equals(userId)) {
                throw new AppException(BusinessError.USER_IS_OWNER);
            }

            // Check if user already has access
            CheckAccessRequest accessRequest = CheckAccessRequest.builder()
                    .documentId(request.getDocumentId())
                    .userId(userId)
                    .build();
            HashMap<String, Object> accessResponse = grantAccessRest.checkGrantAccess(accessRequest);
            HashMap<String, Object> data = (HashMap<String, Object>) accessResponse.get(DATA);
            Boolean canDownload = data.get(CAN_DOWNLOAD) != null && Boolean.TRUE.equals(data.get(CAN_DOWNLOAD));

            if (canDownload) {
                throw new AppException(BusinessError.USER_ALREADY_HAS_ACCESS);
            }

            // Get owner information from UserService
            String ownerEmail = getUserEmail(doc.getOwnerId());
            UserCacheResponse requester = getUserInfo(userId);
            String requesterInfo = formatUserInfo(requester);

            // Send email to owner
            boolean emailSent = sendAccessRequestEmail(
                    ownerEmail,
                    doc.getOriginalFilename(),
                    request.getDocumentId(),
                    requesterInfo,
                    requester, // Pass full user object for detailed display
                    request.getMessage()
            );

            return RequestAccessResponse.builder()
                    .documentId(request.getDocumentId())
                    .requesterUserId(userId)
                    .ownerId(doc.getOwnerId())
                    .ownerEmail(ownerEmail)
                    .emailSent(emailSent)
                    .message("Yêu cầu truy cập đã được gửi đến chủ tài liệu")
                    .build();

        } catch (AppException e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            errorReason = e.getMessage();
            throw new AppException(BusinessError.FAILED_REQUEST_ACCESS, e.getMessage());
        } finally {
            if (doc != null) {
                AuditLogEvent logEvent = AuditLogEvent.builder()
                        .requestId(UUID.randomUUID().toString())
                        .userId(userId)
                        .action(String.valueOf(ActionLog.REQUEST_ACCESS))
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

    /**
     * Enrich document với thông tin permission dựa trên userId
     */
    private void enrichDocumentWithPermissions(DocumentResponse document, String userId) {
        try {
            // Check nếu user là owner
            boolean isOwner = userId.equals(document.getOwnerId());
            document.setIsOwner(isOwner);

            // Nếu là owner, tự động có quyền download và access
            if (isOwner) {
                document.setCanDownload(true);
                document.setHasAccess(true);
            } else {
                // Check quyền truy cập từ AuthAccessControlService
                try {
                    CheckAccessRequest accessRequest = CheckAccessRequest.builder()
                            .documentId(document.getDocumentId())
                            .userId(userId)
                            .build();
                    HashMap<String, Object> accessResponse = grantAccessRest.checkGrantAccess(accessRequest);
                    HashMap<String, Object> data = (HashMap<String, Object>) accessResponse.get(DATA);

                    Boolean canDownload = data.get(CAN_DOWNLOAD) != null && Boolean.TRUE.equals(data.get(CAN_DOWNLOAD));
                    document.setCanDownload(canDownload);
                    document.setHasAccess(canDownload); // Nếu có quyền download thì có access
                } catch (Exception e) {
                    // Nếu không có quyền hoặc lỗi khi check, set false
                    document.setCanDownload(false);
                    document.setHasAccess(false);
                }
            }
        } catch (Exception e) {
            // Nếu có lỗi, set default values
            document.setIsOwner(false);
            document.setCanDownload(false);
            document.setHasAccess(false);
        }
    }

    /**
     * Send access request email to owner with beautiful HTML template
     */
    private boolean sendAccessRequestEmail(String ownerEmail, String documentName, String documentId, String requesterInfo, UserCacheResponse requester, String message) {
        try {
            String htmlContent = buildAccessRequestEmailTemplate(ownerEmail, documentName, documentId, requesterInfo, requester, message);

            // Send HTML email using EmailService
            return emailService.sendHtmlEmail(ownerEmail, "🔐 Yêu cầu truy cập tài liệu", htmlContent);
        } catch (Exception e) {
            System.err.println("Error sending email: " + e.getMessage());
            return false;
        }
    }

    /**
     * Format user information for display
     */
    private String formatUserInfo(UserCacheResponse user) {
        if (user == null) {
            return "Người dùng không xác định";
        }

        StringBuilder info = new StringBuilder();

        // Full name (most important)
        if (user.getFullName() != null && !user.getFullName().trim().isEmpty()) {
            info.append(user.getFullName());
        }

        // Username
        if (user.getUsername() != null && !user.getUsername().trim().isEmpty()) {
            if (info.length() > 0) {
                info.append(" (").append(user.getUsername()).append(")");
            } else {
                info.append(user.getUsername());
            }
        }

        // Email
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
            if (info.length() > 0) {
                info.append(" - ").append(user.getEmail());
            } else {
                info.append(user.getEmail());
            }
        }

        return info.length() > 0 ? info.toString() : "Người dùng không xác định";
    }

    /**
     * Build beautiful HTML email template for access request
     */
    private String buildAccessRequestEmailTemplate(String ownerEmail, String documentName, String documentId, String requesterInfo, UserCacheResponse requester, String message) {
        // Escape HTML special characters to prevent XSS
        String safeDocumentName = escapeHtml(documentName);
        String safeDocumentId = escapeHtml(documentId);
        String safeRequesterInfo = escapeHtml(requesterInfo);
        String safeMessage = message != null && !message.trim().isEmpty() ? escapeHtml(message) : "";

        // Extract user details for detailed display
        String requesterFullName = requester != null && requester.getFullName() != null ? escapeHtml(requester.getFullName()) : "Không xác định";
        String requesterUsername = requester != null && requester.getUsername() != null ? escapeHtml(requester.getUsername()) : "N/A";
        String requesterEmail = requester != null && requester.getEmail() != null ? escapeHtml(requester.getEmail()) : "N/A";

        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Yêu cầu truy cập tài liệu</title>\n" +
                "</head>\n" +
                "<body style=\"margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f5f7fa;\">\n" +
                "    <table role=\"presentation\" style=\"width: 100%; border-collapse: collapse; background-color: #f5f7fa;\">\n" +
                "        <tr>\n" +
                "            <td align=\"center\" style=\"padding: 40px 20px;\">\n" +
                "                <table role=\"presentation\" style=\"max-width: 600px; width: 100%; border-collapse: collapse; background-color: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); overflow: hidden;\">\n" +
                "                    <!-- Header -->\n" +
                "                    <tr>\n" +
                "                        <td style=\"background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 40px 30px; text-align: center;\">\n" +
                "                            <h1 style=\"margin: 0; color: #ffffff; font-size: 28px; font-weight: 600;\">\n" +
                "                                📄 Yêu cầu truy cập tài liệu\n" +
                "                            </h1>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                    \n" +
                "                    <!-- Content -->\n" +
                "                    <tr>\n" +
                "                        <td style=\"padding: 40px 30px;\">\n" +
                "                            <p style=\"margin: 0 0 30px 0; color: #333333; font-size: 16px; line-height: 1.6;\">\n" +
                "                                Xin chào,<br><br>\n" +
                "                                Bạn nhận được <strong style=\"color: #667eea;\">yêu cầu truy cập tài liệu</strong> từ một người dùng trong hệ thống.\n" +
                "                            </p>\n" +
                "                            \n" +
                "                            <!-- Request Info Card -->\n" +
                "                            <div style=\"background-color: #f8f9fa; border-left: 4px solid #667eea; padding: 25px; margin: 30px 0; border-radius: 8px;\">\n" +
                "                                <h2 style=\"margin: 0 0 20px 0; color: #667eea; font-size: 20px; font-weight: 600;\">\n" +
                "                                    👤 Thông tin người yêu cầu\n" +
                "                                </h2>\n" +
                "                                <table role=\"presentation\" style=\"width: 100%; border-collapse: collapse;\">\n" +
                "                                    <tr>\n" +
                "                                        <td style=\"padding: 8px 0; color: #666666; font-size: 14px; width: 120px; vertical-align: top;\">Họ và tên:</td>\n" +
                "                                        <td style=\"padding: 8px 0; color: #333333; font-size: 16px; font-weight: 600;\">" + requesterFullName + "</td>\n" +
                "                                    </tr>\n" +
                "                                    <tr>\n" +
                "                                        <td style=\"padding: 8px 0; color: #666666; font-size: 14px; vertical-align: top;\">Tên đăng nhập:</td>\n" +
                "                                        <td style=\"padding: 8px 0; color: #333333; font-size: 14px; font-family: monospace; background-color: #ffffff; padding: 4px 8px; border-radius: 4px; display: inline-block;\">" + requesterUsername + "</td>\n" +
                "                                    </tr>\n" +
                "                                    <tr>\n" +
                "                                        <td style=\"padding: 8px 0; color: #666666; font-size: 14px; vertical-align: top;\">Email:</td>\n" +
                "                                        <td style=\"padding: 8px 0; color: #333333; font-size: 14px;\"><a href=\"mailto:" + requesterEmail + "\" style=\"color: #667eea; text-decoration: none;\">" + requesterEmail + "</a></td>\n" +
                "                                    </tr>\n" +
                "                                </table>\n" +
                "                            </div>\n" +
                "                            \n" +
                "                            <!-- Document Info Card -->\n" +
                "                            <div style=\"background-color: #fff5f5; border-left: 4px solid #f56565; padding: 25px; margin: 30px 0; border-radius: 8px;\">\n" +
                "                                <h2 style=\"margin: 0 0 15px 0; color: #f56565; font-size: 20px; font-weight: 600;\">\n" +
                "                                    📋 Thông tin tài liệu\n" +
                "                                </h2>\n" +
                "                                <table role=\"presentation\" style=\"width: 100%; border-collapse: collapse;\">\n" +
                "                                    <tr>\n" +
                "                                        <td style=\"padding: 8px 0; color: #666666; font-size: 14px; width: 120px;\">Tên tài liệu:</td>\n" +
                "                                        <td style=\"padding: 8px 0; color: #333333; font-size: 16px; font-weight: 600;\">" + safeDocumentName + "</td>\n" +
                "                                    </tr>\n" +
                "                                    <tr>\n" +
                "                                        <td style=\"padding: 8px 0; color: #666666; font-size: 14px;\">Mã tài liệu:</td>\n" +
                "                                        <td style=\"padding: 8px 0; color: #333333; font-size: 14px; font-family: monospace; background-color: #f8f9fa; padding: 4px 8px; border-radius: 4px;\">" + safeDocumentId + "</td>\n" +
                "                                    </tr>\n" +
                "                                </table>\n" +
                "                            </div>\n" +
                "                            \n" +
                "                            <!-- Message Card (if exists) -->\n" +
                (safeMessage.isEmpty() ? "" :
                        "                            <div style=\"background-color: #fffbf0; border-left: 4px solid #f6ad55; padding: 25px; margin: 30px 0; border-radius: 8px;\">\n" +
                                "                                <h2 style=\"margin: 0 0 15px 0; color: #f6ad55; font-size: 20px; font-weight: 600;\">\n" +
                                "                                    💬 Lời nhắn từ người yêu cầu\n" +
                                "                                </h2>\n" +
                                "                                <p style=\"margin: 0; color: #333333; font-size: 16px; line-height: 1.6; font-style: italic;\">\n" +
                                "                                    \"" + safeMessage + "\"\n" +
                                "                                </p>\n" +
                                "                            </div>\n") +
                "                            \n" +
                "                            <!-- Action Section -->\n" +
                "                            <div style=\"background-color: #e6f7ff; border: 2px solid #1890ff; padding: 25px; margin: 30px 0; border-radius: 8px; text-align: center;\">\n" +
                "                                <p style=\"margin: 0 0 20px 0; color: #333333; font-size: 16px; line-height: 1.6;\">\n" +
                "                                    <strong style=\"color: #1890ff;\">Bước tiếp theo:</strong><br>\n" +
                "                                    Vui lòng đăng nhập vào hệ thống và sử dụng chức năng <strong>chia sẻ tài liệu</strong> để cấp quyền truy cập cho người yêu cầu.\n" +
                "                                </p>\n" +
                "                            </div>\n" +
                "                            \n" +
                "                            <p style=\"margin: 30px 0 0 0; color: #666666; font-size: 14px; line-height: 1.6;\">\n" +
                "                                <strong>Lưu ý:</strong> Email này được gửi tự động từ hệ thống. Vui lòng không trả lời email này.\n" +
                "                            </p>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                    \n" +
                "                    <!-- Footer -->\n" +
                "                    <tr>\n" +
                "                        <td style=\"background-color: #f8f9fa; padding: 30px; text-align: center; border-top: 1px solid #e9ecef;\">\n" +
                "                            <p style=\"margin: 0 0 10px 0; color: #666666; font-size: 14px;\">\n" +
                "                                <strong>Hệ thống chia sẻ tài liệu</strong>\n" +
                "                            </p>\n" +
                "                            <p style=\"margin: 0; color: #999999; font-size: 12px;\">\n" +
                "                                Email này được gửi tự động, vui lòng không trả lời.\n" +
                "                            </p>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                </table>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "    </table>\n" +
                "</body>\n" +
                "</html>";
    }

    /**
     * Escape HTML special characters to prevent XSS attacks
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
