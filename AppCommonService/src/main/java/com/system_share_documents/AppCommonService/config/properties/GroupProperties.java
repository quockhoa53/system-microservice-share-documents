package com.system_share_documents.AppCommonService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "group")
public class GroupProperties {
    private String serviceName;
    private String url;
    private String checkMembership;
    private String getGroupMembers;
}
