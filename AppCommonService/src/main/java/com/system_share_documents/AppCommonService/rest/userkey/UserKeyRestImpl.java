package com.system_share_documents.AppCommonService.rest.userkey;

import com.system_share_documents.AppCommonService.config.properties.UserKeyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

@Repository
public class UserKeyRestImpl implements UserKeyRest {

    @Autowired
    private UserKeyProperties userKeyProperties;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Gọi sang UserService để lấy public key primary cho user.
     *
     * @param userId id của user nhận tài liệu
     * @return public key string hoặc null nếu không tìm thấy
     */
    @Override
    public String getUserPublicKeyForUser(String userId) {
        try {
            String url = userKeyProperties.getUrl() + userKeyProperties.getGetPublicKey() + userId;
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("Error calling UserService: " + e.getMessage());
        }
        return null;
    }
}
