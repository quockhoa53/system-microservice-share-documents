package com.system_share_documents.AppCommonService.config.minio;

import com.system_share_documents.AppCommonService.config.properties.MinioProperties;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Autowired
    private MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        System.out.println("MINIO URL = " + properties.getUrl());
        System.out.println("ACCESS KEY = " + properties.getAccessKey());
        return MinioClient.builder()
                .endpoint(properties.getUrl())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
