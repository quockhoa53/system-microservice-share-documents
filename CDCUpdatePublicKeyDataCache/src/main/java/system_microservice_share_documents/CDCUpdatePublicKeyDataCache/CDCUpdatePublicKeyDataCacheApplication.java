package system_microservice_share_documents.CDCUpdatePublicKeyDataCache;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import system_microservice_share_documents.CDCUpdatePublicKeyDataCache.config.JobConfig;
import system_microservice_share_documents.CDCUpdatePublicKeyDataCache.sink.PublicKeyRedisSink;
import system_microservice_share_documents.CDCUpdatePublicKeyDataCache.transform.CdcDataTransformer;

import java.util.Properties;

public class CDCUpdatePublicKeyDataCacheApplication {

	public static void main(String[] args) throws Exception {
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		env.setParallelism(JobConfig.getInt("flink.parallelism"));
		long checkpointInterval = JobConfig.getLong("flink.checkpoint.interval.ms");
		if (checkpointInterval > 0) env.enableCheckpointing(checkpointInterval);

		Properties kafkaProps = new Properties();
		kafkaProps.setProperty("bootstrap.servers", JobConfig.get("kafka.bootstrap.servers"));
		kafkaProps.setProperty("group.id", JobConfig.get("kafka.group.id"));

		FlinkKafkaConsumer<String> kafkaSource = new FlinkKafkaConsumer<>(
				JobConfig.get("kafka.topic"),
				new SimpleStringSchema(),
				kafkaProps
		);

		env.addSource(kafkaSource)
				.name("Kafka-CDC-Source")
				.assignTimestampsAndWatermarks(WatermarkStrategy.forMonotonousTimestamps())
				.map(new CdcDataTransformer())
				.name("CDC-Data-Transformer")
				.filter(value -> value != null)
				.addSink(new PublicKeyRedisSink())
				.name("Document-Redis-Sink");

		System.out.println("Starting Flink Job: " + JobConfig.get("job.name"));
		env.execute(JobConfig.get("job.name"));
	}
}
