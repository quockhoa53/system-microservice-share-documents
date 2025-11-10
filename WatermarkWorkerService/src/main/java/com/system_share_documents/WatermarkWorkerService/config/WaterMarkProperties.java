package com.system_share_documents.WatermarkWorkerService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "watermark")
public class WaterMarkProperties {
    private int stripes;
    private int localRetries;
}
