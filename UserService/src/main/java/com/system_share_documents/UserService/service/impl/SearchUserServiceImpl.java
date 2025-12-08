package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.service.SearchUserService;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SearchUserServiceImpl implements SearchUserService {

    private static final String USERS_INDEX = "users";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    @Autowired
    private RestHighLevelClient elasticsearchClient;

    @Override
    public List<UserResponse> searchUsers(String query, Integer limit) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        int searchLimit = limit != null ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        String normalizedQuery = query.trim().toLowerCase();

        try {
            SearchRequest searchRequest = new SearchRequest(USERS_INDEX);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                    .should(QueryBuilders.multiMatchQuery(normalizedQuery)
                            .field("username.prefix", 2.0f)
                            .field("email.prefix", 1.5f)
                            .type(MultiMatchQueryBuilder.Type.BOOL_PREFIX)
                            .fuzziness("AUTO"))
                    .must(QueryBuilders.termQuery("status", 1)) // chỉ user active
                    .minimumShouldMatch(1);

            searchSourceBuilder.query(boolQuery);
            searchSourceBuilder.size(searchLimit);
            searchSourceBuilder.fetchSource(true);

            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);

            List<UserResponse> users = new ArrayList<>();
            for (SearchHit hit : searchResponse.getHits().getHits()) {
                Map<String, Object> sourceMap = hit.getSourceAsMap();
                UserResponse user = mapToUserResponse(sourceMap);
                if (user != null) {
                    users.add(user);
                }
            }

            return users;

        } catch (Exception e) {
            log.error("Error searching users in Elasticsearch: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private UserResponse mapToUserResponse(Map<String, Object> sourceMap) {
        try {
            UserResponse.UserResponseBuilder builder = UserResponse.builder();

            // Map id
            if (sourceMap.get("id") != null) {
                builder.id(UUID.fromString(sourceMap.get("id").toString()));
            }

            // Map username
            if (sourceMap.get("username") != null) {
                builder.username(sourceMap.get("username").toString());
            }

            // Map email
            if (sourceMap.get("email") != null) {
                builder.email(sourceMap.get("email").toString());
            }

            // Map fullName
            if (sourceMap.get("full_name") != null) {
                builder.fullName(sourceMap.get("full_name").toString());
            }

            // Map status
            if (sourceMap.get("status") != null) {
                if (sourceMap.get("status") instanceof Number) {
                    builder.status(((Number) sourceMap.get("status")).shortValue());
                } else {
                    builder.status(Short.parseShort(sourceMap.get("status").toString()));
                }
            }

            // Map createdAt (Elasticsearch stores as epoch_millis)
            if (sourceMap.get("created_at") != null) {
                Object createdAt = sourceMap.get("created_at");
                if (createdAt instanceof Number) {
                    long epochMillis = ((Number) createdAt).longValue();
                    builder.createdAt(new Timestamp(epochMillis));
                } else {
                    try {
                        builder.createdAt(new Timestamp(Long.parseLong(createdAt.toString())));
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse created_at: {}", createdAt);
                    }
                }
            }

            // Map updatedAt (Elasticsearch stores as epoch_millis)
            if (sourceMap.get("updated_at") != null) {
                Object updatedAt = sourceMap.get("updated_at");
                if (updatedAt instanceof Number) {
                    long epochMillis = ((Number) updatedAt).longValue();
                    builder.updatedAt(new Timestamp(epochMillis));
                } else {
                    try {
                        builder.updatedAt(new Timestamp(Long.parseLong(updatedAt.toString())));
                    } catch (NumberFormatException e) {
                        log.warn("Could not parse updated_at: {}", updatedAt);
                    }
                }
            }

            // Map profile (JSON object)
            if (sourceMap.get("profile") != null) {
                Object profile = sourceMap.get("profile");
                if (profile instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> profileMap = (Map<String, Object>) profile;
                    builder.profile(profileMap);
                }
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Error mapping Elasticsearch document to UserResponse: {}", e.getMessage(), e);
            return null;
        }
    }
}
