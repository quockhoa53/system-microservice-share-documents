package system_microservice_share_documents.CDCUpdateDocumentDataCache.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RequestOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchConfig {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);
    private static final String ELASTIC_HOST = JobConfig.get("elasticsearch.host");
    private static final int ELASTIC_PORT = JobConfig.getInt("elasticsearch.port");
    private static final String SCHEME = JobConfig.get("elasticsearch.scheme");

    public RestHighLevelClient restHighLevelClient() {
        RestHighLevelClient client = new RestHighLevelClient(
                RestClient.builder(new HttpHost(ELASTIC_HOST, ELASTIC_PORT, SCHEME))
        );
        // Verify connection and flavor early (optional, for debug)
        try {
            // Simple ping or get cluster info
            client.ping(RequestOptions.DEFAULT);
            log.info("Connected to Elasticsearch successfully.");
        } catch (Exception e) {
            log.error("Failed to connect to Elasticsearch: {}", e.getMessage(), e);
            // Don't throw here, let sink handle
        }
        return client;
    }
}