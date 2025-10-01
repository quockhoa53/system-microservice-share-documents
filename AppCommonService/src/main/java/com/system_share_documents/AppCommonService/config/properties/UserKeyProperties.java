package com.system_share_documents.AppCommonService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "user-key")
public class UserKeyProperties {
    private String serviceName;
    private String url;
    private String getPublicKey;
}
