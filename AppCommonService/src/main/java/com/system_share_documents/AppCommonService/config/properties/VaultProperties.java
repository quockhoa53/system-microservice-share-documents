package com.system_share_documents.AppCommonService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {
    private String scheme;
    private String host;
    private int port;
    private String token;
    private String transitKey;
}
