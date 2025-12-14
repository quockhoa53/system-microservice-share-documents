package system_microservice_share_documents.CDCUpdateDocumentDataCache.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;

import java.util.Map;

public class CdcDataTransformer extends RichMapFunction<String, Map<String, Object>> {

    private transient ObjectMapper mapper;
    private transient Counter recordsTransformed;

    @Override
    public void open(Configuration parameters) {
        recordsTransformed = getRuntimeContext()
                .getMetricGroup()
                .counter("recordsTransformed");
        mapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> map(String jsonString) throws Exception {
        recordsTransformed.inc();
        if (jsonString == null || jsonString.isEmpty()) return null;

        if (mapper == null) mapper = new ObjectMapper();

        JsonNode rootNode = mapper.readTree(jsonString);
        JsonNode afterNode = rootNode.get("after");
        JsonNode beforeNode = rootNode.get("before");
        JsonNode opNode = rootNode.get("op");

        if (opNode == null || opNode.isNull()) return null;

        String op = opNode.asText();
        String opType =
                op.equals("c") ? "CREATE" :
                        op.equals("u") ? "UPDATE" :
                                op.equals("d") ? "DELETE" :
                                        op.equals("r") ? "SNAPSHOT" :
                                                null;

        if (opType == null) return null;

        Map<String, Object> documentData;

        if (opType.equals("DELETE")) {
            // dùng before để lấy id
            if (beforeNode == null || beforeNode.isNull()) return null;

            documentData = mapper.readValue(beforeNode.toString(), Map.class);
        } else {
            if (afterNode == null || afterNode.isNull()) return null;
            documentData = mapper.readValue(afterNode.toString(), Map.class);
        }

        // Kiểm tra soft delete: nếu deleted_at != null thì chuyển thành DELETE
        if (!opType.equals("DELETE")) {
            Object deletedAt = documentData.get("deleted_at");
            if (deletedAt != null) {
                // Nếu deleted_at là số (timestamp) và > 0, hoặc là string không rỗng
                boolean isDeleted = false;
                if (deletedAt instanceof Number) {
                    isDeleted = ((Number) deletedAt).longValue() > 0;
                } else if (deletedAt instanceof String) {
                    String deletedAtStr = ((String) deletedAt).trim();
                    isDeleted = !deletedAtStr.isEmpty() && !deletedAtStr.equalsIgnoreCase("null");
                    // Thử parse số nếu là string số
                    try {
                        long timestamp = Long.parseLong(deletedAtStr);
                        isDeleted = timestamp > 0;
                    } catch (NumberFormatException ignored) {
                        // Giữ nguyên giá trị isDeleted từ check string
                    }
                }

                if (isDeleted) {
                    opType = "DELETE";
                }
            }
        }

        documentData.put("_operation", opType);

        // normalize metadata nếu CREATE/UPDATE/SNAPSHOT
        if (!opType.equals("DELETE")) {
            Object metadataVal = documentData.get("metadata");
            if (metadataVal instanceof String) {
                String metaStr = ((String) metadataVal).trim();
                if (!metaStr.isEmpty() && !metaStr.equalsIgnoreCase("null")) {
                    try {
                        Map<String, Object> parsed = mapper.readValue(metaStr, Map.class);
                        documentData.put("metadata", parsed);
                    } catch (Exception e) {
                        documentData.put("metadata", Map.of());
                    }
                } else {
                    documentData.put("metadata", Map.of());
                }
            } else if (metadataVal == null) {
                documentData.put("metadata", Map.of());
            }
        }

        return documentData;
    }

}
