package system_microservice_share_documents.CDCUpdateUserDataCache.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;

import java.util.HashMap;
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

        Map<String, Object> userData;

        if (opType.equals("DELETE")) {
            // dùng before để lấy id
            if (beforeNode == null || beforeNode.isNull()) return null;

            userData = mapper.readValue(beforeNode.toString(), Map.class);
        } else {
            if (afterNode == null || afterNode.isNull()) return null;
            userData = mapper.readValue(afterNode.toString(), Map.class);
        }

        userData.put("_operation", opType);

        // normalize metadata nếu CREATE/UPDATE/SNAPSHOT
        if (!opType.equals("DELETE")) {
            Object metadataVal = userData.get("profile");
            if (metadataVal instanceof String) {
                String metaStr = ((String) metadataVal).trim();
                if (!metaStr.isEmpty() && !metaStr.equalsIgnoreCase("null")) {
                    try {
                        Map<String, Object> parsed = mapper.readValue(metaStr, Map.class);
                        userData.put("profile", parsed);
                    } catch (Exception e) {
                        userData.put("profile", new HashMap<>());
                    }
                } else {
                    userData.put("profile", new HashMap<>());
                }
            } else if (metadataVal == null) {
                userData.put("profile", new HashMap<>());
            }
        }

        return userData;
    }

}
