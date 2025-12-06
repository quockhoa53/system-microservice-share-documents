package system_microservice_share_documents.CDCUpdatePublicKeyDataCache.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.MapFunction;

import java.util.Map;

public class CdcDataTransformer implements MapFunction<String, Map<String, Object>> {

    private transient ObjectMapper mapper;

    @Override
    public Map<String, Object> map(String jsonString) throws Exception {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }

        if (mapper == null) {
            mapper = new ObjectMapper();
        }

        JsonNode rootNode = mapper.readTree(jsonString);
        JsonNode afterNode = rootNode.get("after");
        JsonNode opNode = rootNode.get("op");

        if (afterNode == null || afterNode.isNull() || opNode == null || opNode.isNull()) {
            return null;
        }

        String op = opNode.asText();
        String opType;
        switch (op) {
            case "c":
                opType = "CREATE";
                break;
            case "u":
                opType = "UPDATE";
                break;
            case "d":
                opType = "DELETE";
                break;
            case "r":
                opType = "SNAPSHOT";
                break;
            default:
                return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> documentData = mapper.readValue(afterNode.toString(), Map.class);
        documentData.put("_operation", opType);

        return documentData;
    }
}
