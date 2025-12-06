package com.system_share_documents.AppCommonService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grant-access")
public class GrantAccessProperties {
    private String serviceName;
    private String url;
    private String checkGrantAccess;
    private String createGrantAccess;
}
