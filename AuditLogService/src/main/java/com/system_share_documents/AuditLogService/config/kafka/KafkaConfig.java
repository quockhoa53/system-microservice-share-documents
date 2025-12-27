package com.system_share_documents.AuditLogService.config.kafka;

import com.system_share_documents.AppCommonService.event.MalwareScanResultEvent;
import com.system_share_documents.AuditLogService.dto.request.CreateAuditLogRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ConsumerFactory<String, CreateAuditLogRequest> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-log-service");
        
        // Sử dụng ErrorHandlingDeserializer để xử lý lỗi deserialization tốt hơn
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class.getName());
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.system_share_documents.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CreateAuditLogRequest.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CreateAuditLogRequest> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CreateAuditLogRequest> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3); // 3 thread consume song song
        
        // Thêm error handler để log lỗi và skip message lỗi
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error("[AuditLogConsumer] Failed to process audit log record: {}", record, exception);
                },
                new FixedBackOff(0L, 0) // Không retry, skip message lỗi ngay
        );
        factory.setCommonErrorHandler(errorHandler);
        
        return factory;
    }

    @Bean
    public ConsumerFactory<String, MalwareScanResultEvent> malwareScanResultConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "audit-log-service-malware-alert");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Sử dụng ErrorHandlingDeserializer để xử lý lỗi deserialization tốt hơn
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class.getName());
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());

        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.system_share_documents.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MalwareScanResultEvent.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MalwareScanResultEvent> malwareScanResultKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, MalwareScanResultEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(malwareScanResultConsumerFactory());
        factory.setConcurrency(3); // 3 thread consume song song

        // Thêm error handler để log lỗi và skip message lỗi
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    log.error("[MalwareScanResultConsumer] Failed to process malware scan result record: {}", record, exception);
                },
                new FixedBackOff(0L, 0) // Không retry, skip message lỗi ngay
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
