package com.system_share_documents.AppCommonService.config.rest;

import com.system_share_documents.AppCommonService.rest.token.KeycloakTokenRest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Autowired
    private KeycloakTokenRest keycloakTokenRest;

    @Bean(name = "userKeyRestTemplate")
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            String token = keycloakTokenRest.getAccessToken();
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, body);
        };

        return builder
                .additionalInterceptors(List.of(authInterceptor))
                .build();
    }
}
