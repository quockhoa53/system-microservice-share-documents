package com.system_share_documents.AppCommonService.config.rest;

import com.system_share_documents.AppCommonService.rest.token.KeycloakTokenRest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Configuration
public class RestTemplateConfig {

    @Autowired
    private KeycloakTokenRest keycloakTokenRest;

    @Bean(name = "userKeyRestTemplate")
    @LoadBalanced
    public RestTemplate userKeyrestTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            String token = keycloakTokenRest.getAccessToken();
            request.getHeaders().setBearerAuth(token);
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return execution.execute(request, body);
        };

        return builder
                .additionalInterceptors(List.of(authInterceptor))
                .build();
    }

    @Bean(name = "documentKeyRestTemplate")
    @LoadBalanced
    public RestTemplate documentKeyrestTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            String token = keycloakTokenRest.getAccessToken();
            request.getHeaders().setBearerAuth(token);
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return execution.execute(request, body);
        };

        return builder
                .additionalInterceptors(List.of(authInterceptor))
                .build();
    }

    @Bean(name = "grantAccessRestTemplate")
    @LoadBalanced
    public RestTemplate grantAccessrestTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            String token = keycloakTokenRest.getAccessToken();
            request.getHeaders().setBearerAuth(token);
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return execution.execute(request, body);
        };

        return builder
                .additionalInterceptors(List.of(authInterceptor))
                .build();
    }

    @Bean(name = "groupRestTemplate")
    @LoadBalanced
    public RestTemplate grouprestTemplate(RestTemplateBuilder builder) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            String token = keycloakTokenRest.getAccessToken();
            request.getHeaders().setBearerAuth(token);
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return execution.execute(request, body);
        };

        return builder
                .additionalInterceptors(List.of(authInterceptor))
                .build();
    }
}
