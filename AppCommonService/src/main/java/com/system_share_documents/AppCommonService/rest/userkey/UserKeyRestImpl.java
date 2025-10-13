package com.system_share_documents.AppCommonService.rest.userkey;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.config.properties.UserKeyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

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
            String prefix = userKeyProperties.getGetPublicKey().replace("{userId}", userId);
            String url = userKeyProperties.getUrl() + prefix + "?keyType=" + keyType;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
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
            e.printStackTrace();
            return null;
        }
    }

}
