package system_microservice_share_documents.CDCUpdateDocumentDataCache;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import system_microservice_share_documents.CDCUpdateDocumentDataCache.config.JobConfig;
import system_microservice_share_documents.CDCUpdateDocumentDataCache.deserializa.NullableStringSchema;
import system_microservice_share_documents.CDCUpdateDocumentDataCache.sink.DocumentElasticsearchSink;
import system_microservice_share_documents.CDCUpdateDocumentDataCache.sink.DocumentRedisSink;
import system_microservice_share_documents.CDCUpdateDocumentDataCache.transform.CdcDataTransformer;

import java.util.Properties;

public class CDCUpdateDocumentDataCacheApplication {

	public static void main(String[] args) throws Exception {
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		env.setParallelism(JobConfig.getInt("flink.parallelism"));
		long checkpointInterval = JobConfig.getLong("flink.checkpoint.interval.ms");
		if (checkpointInterval > 0) env.enableCheckpointing(checkpointInterval);

		Properties kafkaProps = new Properties();
		kafkaProps.setProperty("bootstrap.servers", JobConfig.get("kafka.bootstrap.servers"));
		kafkaProps.setProperty("group.id", JobConfig.get("kafka.group.id"));

		FlinkKafkaConsumer<String> kafkaSource1 = new FlinkKafkaConsumer<>(
				JobConfig.get("kafka.topic"),
				new NullableStringSchema(),
				kafkaProps
		);

		FlinkKafkaConsumer<String> kafkaSource2 = new FlinkKafkaConsumer<>(
				JobConfig.get("kafka.topic"),
				new NullableStringSchema(),
				kafkaProps
		);

		env.addSource(kafkaSource1)
				.name("Kafka-CDC-Source")
				.assignTimestampsAndWatermarks(WatermarkStrategy.noWatermarks())
				.map(new CdcDataTransformer())
				.filter(value -> value != null)
				.addSink(new DocumentRedisSink())
				.setParallelism(2);

		env.addSource(kafkaSource2)
				.name("Kafka-CDC-Source-ES")
				.assignTimestampsAndWatermarks(WatermarkStrategy.noWatermarks())
				.map(new CdcDataTransformer())
				.filter(value -> value != null)
				.addSink(new DocumentElasticsearchSink())
				.setParallelism(2);

		System.out.println("Starting Flink Job: " + JobConfig.get("job.name"));
		env.execute(JobConfig.get("job.name"));
	}
}
