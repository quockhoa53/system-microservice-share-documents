package com.system_share_documents.UserService.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfig {

    @Value("${app.security.keycloak.server-url:http://localhost:9090}")
    private String serverUrl;

    /**
     * Realm để login admin (thường là master - realm quản lý của Keycloak)
     * Admin user từ realm master có thể quản lý tất cả realms
     */
    @Value("${app.security.keycloak.admin-realm:master}")
    private String adminRealm;

    @Value("${app.security.keycloak.admin-username:admin}")
    private String adminUsername;

    @Value("${app.security.keycloak.admin-password:admin}")
    private String adminPassword;

    @Value("${app.security.keycloak.client-id:admin-cli}")
    private String clientId;

    @Bean
    public Keycloak keycloakAdmin() {
        // Login vào admin realm (master) để có quyền quản lý tất cả realms
        // Sau đó trong KeycloakGroupServiceImpl sẽ dùng realm system-share-docs để quản lý groups
        return KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(adminRealm)  // Login vào realm master
                .username(adminUsername)
                .password(adminPassword)
                .clientId(clientId)
                .build();
    }
}












