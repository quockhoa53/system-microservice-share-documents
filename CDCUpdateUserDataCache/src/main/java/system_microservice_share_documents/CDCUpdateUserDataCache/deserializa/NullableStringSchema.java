package system_microservice_share_documents.CDCUpdateUserDataCache.deserializa;

import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class NullableStringSchema implements DeserializationSchema<String> {

    @Override
    public String deserialize(byte[] message) throws IOException {
        if (message == null) return null; // xử lý tombstone
        return new String(message, StandardCharsets.UTF_8);
    }

    @Override
    public boolean isEndOfStream(String nextElement) {
        return false;
    }

    @Override
    public TypeInformation<String> getProducedType() {
        return TypeInformation.of(String.class);
    }
}
