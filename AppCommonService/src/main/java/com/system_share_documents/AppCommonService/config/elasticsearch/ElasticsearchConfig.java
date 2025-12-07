package com.system_share_documents.AppCommonService.config.elasticsearch;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    private static final String ELASTIC_HOST = "localhost";
    private static final int ELASTIC_PORT = 9200;
    private static final String SCHEME = "http";

    @Bean(destroyMethod = "close")
    public RestHighLevelClient restHighLevelClient() {
        return new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost(ELASTIC_HOST, ELASTIC_PORT, SCHEME)
                )
        );
    }
}
