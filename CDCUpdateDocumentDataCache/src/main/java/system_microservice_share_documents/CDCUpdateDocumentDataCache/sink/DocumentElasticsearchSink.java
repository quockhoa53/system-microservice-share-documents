package system_microservice_share_documents.CDCUpdateDocumentDataCache.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import system_microservice_share_documents.CDCUpdateDocumentDataCache.config.ElasticsearchConfig;

import java.util.HashMap;
import java.util.Map;

public class DocumentElasticsearchSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(DocumentElasticsearchSink.class);

    private transient RestHighLevelClient client;
    private transient Counter recordsSinked;
    private transient ObjectMapper mapper;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        client = new ElasticsearchConfig().restHighLevelClient();
        recordsSinked = getRuntimeContext().getMetricGroup().counter("recordsSinkedES");
        mapper = new ObjectMapper();

        // ensure index exists
        GetIndexRequest existsRequest = new GetIndexRequest("documents");
        boolean exists = client.indices().exists(existsRequest, RequestOptions.DEFAULT);

        if (!exists) {
            log.info("Index 'documents' does not exist → creating...");
            CreateIndexRequest request = new CreateIndexRequest("documents");
            client.indices().create(request, RequestOptions.DEFAULT);
        } else {
            log.info("Index 'documents' already exists. Skip creation.");
        }
    }

    @Override
    public void invoke(Map<String, Object> documentData, Context context) {
        if (documentData == null) return;

        Object idObj = documentData.get("id");
        if (idObj == null) {
            log.error("Missing ID field. Skipping record: {}", documentData);
            return;
        }

        String id = idObj.toString();
        String operation = String.valueOf(documentData.get("_operation"));

        switch (operation) {
            case "CREATE":
            case "UPDATE":
            case "SNAPSHOT":
                indexDocument(id, documentData);
                break;

            case "DELETE":
                deleteDocument(id);
                break;

            default:
                log.warn("Unknown _operation: {} for ID={}", operation, id);
        }
    }

    private void indexDocument(String id, Map<String, Object> source) {
        try {
            // make safe copy
            Map<String, Object> safeSource = new HashMap<>(source);

            // remove _operation before storing
            safeSource.remove("_operation");

            // --- Strong sanitize metadata (100% ES-safe) ---
            Object metaVal = safeSource.remove("metadata"); // REMOVE FIRST

            Map<String, Object> metaMap = new HashMap<>();

            if (metaVal instanceof Map) {
                metaMap = (Map<String, Object>) metaVal;
            } else if (metaVal instanceof String) {
                String s = ((String) metaVal).trim();
                if (!s.isEmpty() && !s.equalsIgnoreCase("null")) {
                    try {
                        metaMap = mapper.readValue(s, Map.class);
                    } catch (Exception e) {
                        log.warn("[ES] metadata parse failed for id {}: {} → using empty object",
                                id, e.getMessage());
                    }
                }
            }

            safeSource.put("metadata", metaMap);

            IndexRequest request = new IndexRequest("documents")
                    .id(id)
                    .source(safeSource);

            client.indexAsync(request, RequestOptions.DEFAULT,
                    new ActionListener<IndexResponse>() {
                        @Override
                        public void onResponse(IndexResponse indexResponse) {
                            recordsSinked.inc();
                            log.info("[ES] Indexed document {}", id);
                        }

                        @Override
                        public void onFailure(Exception e) {
                            log.error("[ES] Failed to index {}: {}", id, e.getMessage(), e);
                        }
                    });

        } catch (Exception e) {
            log.error("[ES] Error preparing document {}: {}", id, e.getMessage(), e);
        }
    }

    private void deleteDocument(String id) {
        DeleteRequest request = new DeleteRequest("documents", id);

        client.deleteAsync(request, RequestOptions.DEFAULT,
                new ActionListener<DeleteResponse>() {
                    @Override
                    public void onResponse(DeleteResponse deleteResponse) {
                        recordsSinked.inc();
                        log.info("[ES] Deleted {}", id);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        log.error("[ES] Delete failed {}: {}", id, e.getMessage());
                    }
                });
    }

    @Override
    public void close() throws Exception {
        if (client != null) client.close();
        super.close();
    }
}
