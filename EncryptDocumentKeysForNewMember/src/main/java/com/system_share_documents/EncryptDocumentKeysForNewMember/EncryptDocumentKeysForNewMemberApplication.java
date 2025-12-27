package com.system_share_documents.EncryptDocumentKeysForNewMember;

import com.system_share_documents.EncryptDocumentKeysForNewMember.entity.MemberJoinedGroupEvent;
import com.system_share_documents.EncryptDocumentKeysForNewMember.mapper.EncryptionProcessorMapper;
import com.system_share_documents.EncryptDocumentKeysForNewMember.mapper.JsonToEventMapper;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;

import java.io.InputStream;
import java.util.Properties;

/**
 * Flink Job: Mã hóa CEK cho member mới join group
 *
 * Workflow:
 * 1. Consume event MEMBER_JOINED_GROUP từ Kafka
 * 2. Lấy tất cả documents trong group
 * 3. Với mỗi document version:
 *    - Lấy wrappedCEKMaster từ Vault
 *    - Decrypt CEK master
 *    - Lấy public key của member mới
 *    - Encrypt CEK với public key của member
 *    - Lưu DocumentKey vào database
 */
public class EncryptDocumentKeysForNewMemberApplication {

    public static void main(String[] args) throws Exception {
        // Load application.properties
        Properties props = loadProperties("application.properties");

        // Kafka config
        String bootstrapServers = props.getProperty("bootstrap.servers");
        String groupId = props.getProperty("group.id");
        String topic = props.getProperty("topic");

        // Initialize Flink environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.enableCheckpointing(5000); // Checkpoint mỗi 5 giây
        env.setParallelism(Integer.parseInt(props.getProperty("parallelism", "4")));

        // Kafka consumer properties
        Properties consumerProps = new Properties();
        consumerProps.setProperty("bootstrap.servers", bootstrapServers);
        consumerProps.setProperty("group.id", groupId);
        consumerProps.setProperty("auto.offset.reset", "latest");

        // Kafka source
        FlinkKafkaConsumer<String> kafkaSource = new FlinkKafkaConsumer<>(
                topic,
                new SimpleStringSchema(),
                consumerProps
        );

        // Map JSON -> POJO và xử lý
        DataStream<MemberJoinedGroupEvent> eventStream = env
                .addSource(kafkaSource)
                .map(new JsonToEventMapper())
                .filter(event -> event != null);

        // Process event: Mã hóa CEK cho member mới với tất cả documents trong group
        eventStream
                .keyBy(MemberJoinedGroupEvent::getGroupId) // Group by groupId để xử lý theo thứ tự
                .map(new EncryptionProcessorMapper());

        System.out.println("🚀 Starting Flink job: EncryptDocumentKeysForNewMember");
        env.execute("encrypt-document-keys-for-new-member");
    }

    private static Properties loadProperties(String filename) throws Exception {
        Properties props = new Properties();
        try (InputStream in = EncryptDocumentKeysForNewMemberApplication.class.getClassLoader()
                .getResourceAsStream(filename)) {
            if (in == null) {
                throw new RuntimeException("File not found: " + filename);
            }
            props.load(in);
        }
        return props;
    }
}



















