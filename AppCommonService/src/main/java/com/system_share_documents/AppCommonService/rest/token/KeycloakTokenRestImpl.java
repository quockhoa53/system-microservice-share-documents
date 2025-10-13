package com.system_share_documents.AppCommonService.rest.token;

import com.system_share_documents.AppCommonService.config.properties.KeyCloakTokenProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

import static com.system_share_documents.AppCommonService.constant.KeyCloakConstant.*;

@Repository
public class KeycloakTokenRestImpl implements KeycloakTokenRest {

    @Autowired
    private KeyCloakTokenProperties keyCloakTokenProperties;

    private final RestTemplate restTemplate = new RestTemplate();
    private String cachedToken;
    private Instant expiryTime = Instant.EPOCH;

    @Override
    public synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(expiryTime)) {
            return cachedToken;
        }
        try {
            var body = new LinkedMultiValueMap<String, String>();
            body.add(GRANT_TYPE, "client_credentials");
            body.add(CLIENT_ID, keyCloakTokenProperties.getClientId());
            body.add(CLIENT_SECRET, keyCloakTokenProperties.getClientSecret());
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            var request = new HttpEntity<>(body, headers);
            var response = restTemplate.postForEntity(keyCloakTokenProperties.getTokenBaseUrl(), request, Map.class);
            Map<?, ?> bodyMap = response.getBody();
            if (bodyMap == null || bodyMap.get("access_token") == null) {
                throw new RuntimeException("Invalid token response from Keycloak: " + bodyMap);
            }
            cachedToken = (String) bodyMap.get("access_token");
            Object exp = bodyMap.get("expires_in");
            long expSec = (exp instanceof Number) ? ((Number) exp).longValue() : Long.parseLong(exp != null ? exp.toString() : "300");
            expiryTime = Instant.now().plusSeconds(Math.max(30, expSec - 30));
            return cachedToken;

        } catch (HttpClientErrorException e) {
            throw new RuntimeException("Failed to get token from Keycloak", e);
        } catch (ResourceAccessException e) {
            throw new RuntimeException("Keycloak server unreachable", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while getting token", e);
        }
    }

}
