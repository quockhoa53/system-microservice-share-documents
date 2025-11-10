package com.system_share_documents.AppCommonService.rest.userkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.config.properties.UserKeyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Repository
public class UserKeyRestImpl implements UserKeyRest {

    @Autowired
    private UserKeyProperties userKeyProperties;

    @Autowired
    @Qualifier("userKeyRestTemplate")
    private RestTemplate restTemplate;

    /**
     * Gọi sang UserService để lấy public primary key primary cho user.
     * Gọi sang UserService để lấy public primary key primary cho user.
     *
     * @param userId id của user nhận tài liệu
     * @param keyType loại openpgp-ed25519
     * @return public key string hoặc null nếu không tìm thấy
     */
    @Override
    public String getUserPublicPrimaryKeyForUser(String userId, String keyType) {
        try {
            String url = userKeyProperties.getUrl() + userKeyProperties.getGetPublicKey();
            Map<String, Object> body = new HashMap<>();
            body.put("userId", UUID.fromString(userId));
            body.put("keyType", keyType);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            String responseBody = response.getBody();
            if (responseBody != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(responseBody);
                JsonNode dataNode = root.path("data");
                if (!dataNode.isMissingNode()) {
                    return dataNode.path("publicKeyArmored").asText(null);
                }
            }
            return null;

        } catch (Exception e) {
            System.err.println("Error calling UserService: " + e.getMessage());
            return null;
        }
    }
}
