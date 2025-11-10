package com.system_share_documents.AppCommonService.config.kafka;

import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.AppCommonService.event.WatermarkProcessEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private Map<String, Object> baseConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return props;
    }

    // Producer cho AuditLog
    @Bean
    public ProducerFactory<String, AuditLogEvent> producerAuditLogFactory() {
        return new DefaultKafkaProducerFactory<>(baseConfig());
    }

    @Bean
    public KafkaTemplate<String, AuditLogEvent> kafkaTemplateAuditLog() {
        return new KafkaTemplate<>(producerAuditLogFactory());
    }

    // Producer cho WorkerJob
    @Bean
    public ProducerFactory<String, WatermarkJobEvent> producerWatermarkJobFactory() {
        return new DefaultKafkaProducerFactory<>(baseConfig());
    }

    @Bean
    public KafkaTemplate<String, WatermarkJobEvent> kafkaTemplateWatermarkJob() {
        return new KafkaTemplate<>(producerWatermarkJobFactory());
    }

    // Producer cho Watermark Process
    @Bean
    public ProducerFactory<String, WatermarkProcessEvent> producerWatermarkProcessFactory() {
        return new DefaultKafkaProducerFactory<>(baseConfig());
    }

    @Bean
    public KafkaTemplate<String, WatermarkProcessEvent> kafkaTemplateWatermarkProcess() {
        return new KafkaTemplate<>(producerWatermarkProcessFactory() );
    }
}
