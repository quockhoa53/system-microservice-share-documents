package com.system_share_documents.AppCommonService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "keycloak")
public class KeyCloakTokenProperties {
    private String tokenBaseUrl;
    private String realm;
    private String clientId;
    private String clientSecret;
}
