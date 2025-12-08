package system_microservice_share_documents.CDCUpdateUserDataCache;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import system_microservice_share_documents.CDCUpdateUserDataCache.config.JobConfig;
import system_microservice_share_documents.CDCUpdateUserDataCache.deserializa.NullableStringSchema;
import system_microservice_share_documents.CDCUpdateUserDataCache.sink.UserElasticsearchSink;
import system_microservice_share_documents.CDCUpdateUserDataCache.sink.UserRedisSink;
import system_microservice_share_documents.CDCUpdateUserDataCache.transform.CdcDataTransformer;

import java.util.Properties;

public class CDCUpdateUserDataCacheApplication {

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
				.addSink(new UserRedisSink())
				.setParallelism(2);

		env.addSource(kafkaSource2)
				.name("Kafka-CDC-Source-ES")
				.assignTimestampsAndWatermarks(WatermarkStrategy.noWatermarks())
				.map(new CdcDataTransformer())
				.filter(value -> value != null)
				.addSink(new UserElasticsearchSink())
				.setParallelism(2);

		System.out.println("Starting Flink Job: " + JobConfig.get("job.name"));
		env.execute(JobConfig.get("job.name"));
	}
}
