package com.system_share_documents.WatermarkWorkerService.config;

import com.system_share_documents.WatermarkWorkerService.concurrent.StripedExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

    @Bean
    public StripedExecutor stripedExecutor(WaterMarkProperties properties, MeterRegistry registry) {
        return new StripedExecutor(properties.getStripes(), registry);
    }
}
