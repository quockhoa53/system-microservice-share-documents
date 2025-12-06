package com.system_share_documents.AppCommonService.rest.documentKey;

import com.system_share_documents.AppCommonService.config.properties.DocumentKeyProperties;
import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DocumentKeyRestImpl implements DocumentKeyRest {

    @Autowired
    private DocumentKeyProperties properties;

    @Autowired
    @Qualifier("documentKeyRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public void createAndSaveKey(CreateDocumentKeyRequest request) throws Exception {
        String url = properties.getUrl() + properties.getCreateDocumentKey();
        HttpEntity<CreateDocumentKeyRequest> entity = new HttpEntity<>(request, null);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Create Document Key API failed with status: " + response.getStatusCode());
            } else {
                System.out.printf("🗝️ Create Document Key API returned: %s\n", response.getBody());
            }
        } catch (Exception ex) {
            throw new Exception("Error calling Document Key API: " + ex.getMessage(), ex);
        }

    }
}
