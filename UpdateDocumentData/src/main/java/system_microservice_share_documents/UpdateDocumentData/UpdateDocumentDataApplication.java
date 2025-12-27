package system_microservice_share_documents.UpdateDocumentData;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import system_microservice_share_documents.UpdateDocumentData.entity.WatermarkProcessEvent;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

public class UpdateDocumentDataApplication {

	public static void main(String[] args) throws Exception {
		// Load application.properties
		Properties props = loadProperties("application.properties");

		// Kafka config
		String bootstrapServers = props.getProperty("bootstrap.servers");
		String groupId = props.getProperty("group.id");
		String topic = props.getProperty("topic");

		// DB config
		String dbUrl = props.getProperty("db.url");
		String dbUser = props.getProperty("db.user");
		String dbPass = props.getProperty("db.password");

		// Initialize Flink environment
		StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.enableCheckpointing(5000);

		// Kafka consumer properties
		Properties consumerProps = new Properties();
		consumerProps.setProperty("bootstrap.servers", bootstrapServers);
		consumerProps.setProperty("group.id", groupId);

		ObjectMapper objectMapper = new ObjectMapper();

		// Kafka source
		FlinkKafkaConsumer<String> kafkaSource = new FlinkKafkaConsumer<>(
				topic,
				new SimpleStringSchema(),
				consumerProps
		);

		// Map JSON -> POJO safely
		DataStream<WatermarkProcessEvent> eventStream = env
				.addSource(kafkaSource)
				.map(json -> {
					try {
						if (json == null || json.trim().isEmpty()) {
							System.err.println("⚠️ Received empty JSON record from Kafka, skipping");
							return null;
						}
						WatermarkProcessEvent event = objectMapper.readValue(json, WatermarkProcessEvent.class);
						System.out.println("📥 Received from Kafka: " + json);
						return event;
					} catch (Exception e) {
						System.err.println("❌ Failed to parse JSON: " + json);
						e.printStackTrace();
						return null;
					}
				})
				.filter(event -> event != null);

		// THÊM: Map để check status trước khi sink - skip nếu QUARANTINED
		DataStream<WatermarkProcessEvent> filteredEventStream = eventStream
				.map(event -> {
					String currentStatus = getCurrentStatus(dbUrl, dbUser, dbPass, event.getVersionId());
					if ("QUARANTINED".equals(currentStatus)) {
						System.err.println("⚠️ [Watermark] Skip update: versionId=" + event.getVersionId() + " already QUARANTINED (status=" + currentStatus + ")");
						return null;  // Skip event
					}
					System.err.println("🔄 [Watermark] Proceed update: versionId=" + event.getVersionId() + ", current status=" + currentStatus);
					return event;
				})
				.filter(event -> event != null);  // Filter null (skipped events)

		// JDBC Sink (chỉ cho events không bị skip)
		filteredEventStream.addSink(
				JdbcSink.sink(
						"UPDATE public.document_versions " +  // add schema
								"SET watermarked = true, " +
								"    status = ?, " +
								"    storage_object_key = ?, " +
								"    checksum = ?, " +
								"    updated_at = ? " +
								"WHERE id = ?",
						(ps, event) -> {
							try {
								System.out.println("📥 Updating document_versions, id = " + event.getVersionId());
								ps.setString(1, "AVAILABLE");
								ps.setString(2, event.getWatermarkedKey());
								ps.setString(3, event.getChecksum());
								ps.setTimestamp(4, Timestamp.from(Instant.now()));
								UUID versionUuid = UUID.fromString(event.getVersionId());
								ps.setObject(5, versionUuid, java.sql.Types.OTHER);
							} catch (Exception e) {
								System.err.println("❌ JDBC Sink exception for versionId = " + event.getVersionId());
								e.printStackTrace();
							}
						},
						JdbcExecutionOptions.builder()
								.withBatchSize(1)
								.withBatchIntervalMs(200)
								.withMaxRetries(3)
								.build(),
						new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
								.withUrl(dbUrl)
								.withDriverName("org.postgresql.Driver")
								.withUsername(dbUser)
								.withPassword(dbPass)
								.build()
				)
		);

		env.execute("update-data-after-watermark-documents");
	}

	/**
	 * THÊM: Helper method để query current status của version
	 * @param dbUrl JDBC URL
	 * @param dbUser Username
	 * @param dbPass Password
	 * @param versionId Version UUID string
	 * @return Current status, or null if not found
	 */
	private static String getCurrentStatus(String dbUrl, String dbUser, String dbPass, String versionId) {
		try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
			 PreparedStatement ps = conn.prepareStatement("SELECT status FROM public.document_versions WHERE id = ?")) {
			ps.setObject(1, UUID.fromString(versionId));
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getString("status");
				}
			}
		} catch (SQLException e) {
			System.err.println("❌ Failed to query status for versionId=" + versionId + ": " + e.getMessage());
			e.printStackTrace();
			// Fail-open: Assume not QUARANTINED, proceed update
			return null;
		}
		return null;
	}

	private static Properties loadProperties(String filename) throws Exception {
		Properties props = new Properties();
		try (InputStream in = UpdateDocumentDataApplication.class.getClassLoader().getResourceAsStream(filename)) {
			if (in == null) throw new RuntimeException("File not found: " + filename);
			props.load(in);
		}
		return props;
	}
}