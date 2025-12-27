package com.system_share_documents.AppCommonService.rest.grantAccess;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.config.properties.GrantAccessProperties;
import com.system_share_documents.AppCommonService.dto.request.CheckAccessRequest;
import com.system_share_documents.AppCommonService.dto.request.GrantAccessRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;

@Repository
public class GrantAccessRestImpl implements GrantAccessRest {

    @Autowired
    private GrantAccessProperties grantAccessProperties;

    @Autowired
    @Qualifier("grantAccessRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public HashMap<String, Object> checkGrantAccess(CheckAccessRequest request) throws Exception {
        try {
            String url = grantAccessProperties.getUrl() + grantAccessProperties.getCheckGrantAccess();
            HttpEntity<CheckAccessRequest> entity = new HttpEntity<>(request, null);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            ObjectMapper mapper = new ObjectMapper();
            HashMap<String, Object> result = mapper.readValue(response.getBody(), new TypeReference<HashMap<String, Object>>() {});
            return result;
        } catch (Exception e) {
            throw new Exception("Error when calling checkGrantAccess API: " + e.getMessage(), e);
        }
    }

    @Override
    public HashMap<String, Object> createGrantAccess(GrantAccessRequest request) throws Exception {
        try {
            String url = grantAccessProperties.getUrl() + grantAccessProperties.getCreateGrantAccess();
            HttpEntity<GrantAccessRequest> entity = new HttpEntity<>(request, null);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            ObjectMapper mapper = new ObjectMapper();
            HashMap<String, Object> result = mapper.readValue(response.getBody(), new TypeReference<HashMap<String, Object>>() {});
            return result;
        } catch (Exception e) {
            throw new Exception("Error when calling createGrantAccess API: " + e.getMessage(), e);
        }
    }


    @Override
    public void upsertGroupDocumentRecipient(String documentId, String groupId, String accessRole) throws Exception {
        try {
            String url = grantAccessProperties.getUrl() + grantAccessProperties.getUpsertGroupDocumentRecipient();
            HashMap<String, String> request = new HashMap<>();
            request.put("documentId", documentId);
            request.put("groupId", groupId);
            request.put("accessRole", accessRole);
            HttpEntity<HashMap<String, String>> entity = new HttpEntity<>(request, null);
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            throw new Exception("Error when calling upsertGroupDocumentRecipient API: " + e.getMessage(), e);
        }
    }

}
