package com.system_share_documents.AppCommonService.rest.group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.config.properties.GroupProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Repository
public class GroupRestImpl implements GroupRest {

    @Autowired
    private GroupProperties groupProperties;

    @Autowired
    @Qualifier("groupRestTemplate")
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> checkMembership(UUID groupId, UUID userId) {
        try {
            String url = groupProperties.getUrl() + groupProperties.getCheckMembership();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class,
                    groupId,
                    userId
            );
            String responseBody = response.getBody();
            if (responseBody != null) {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode dataNode = root.path("data");
                if (dataNode.isObject()) {
                    return objectMapper.convertValue(dataNode, Map.class);
                }
            }
            return null;
        } catch (Exception e) {
            System.out.println("Error calling UserService for membership check: " + e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getGroupMembers(UUID groupId) {
        try {
            String url = groupProperties.getUrl() + groupProperties.getGetGroupMembers();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    String.class,
                    groupId
            );
            if (response.getBody() != null) {
                JsonNode dataNode = objectMapper.readTree(response.getBody()).path("data");
                if (dataNode.isArray()) {
                    List<Map<String, Object>> members = new ArrayList<>();
                    for (JsonNode memberNode : dataNode) {
                        members.add(objectMapper.convertValue(memberNode, Map.class));
                    }
                    return members;
                }
            }
            return Collections.emptyList();
        } catch (Exception e) {
            System.out.println("Error calling UserService for group members: " + e);
            return Collections.emptyList();
        }
    }

}

