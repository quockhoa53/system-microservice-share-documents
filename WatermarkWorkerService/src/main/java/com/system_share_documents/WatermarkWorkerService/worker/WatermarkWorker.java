package com.system_share_documents.WatermarkWorkerService.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.WatermarkWorkerService.concurrent.StripedExecutor;
import com.system_share_documents.WatermarkWorkerService.service.WatermarkProcessorService;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import static com.system_share_documents.AppCommonService.constant.GroupIdKafkaConstant.WATERMARK_WORKERS_GROUP;
import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.DOCUMENT_WATERMARK_PROCESSED_TOPIC;
import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.DOCUMENT_WATERMARK_REQUEST_TOPIC;

@Component
public class WatermarkWorker {
    private static final Logger log = LoggerFactory.getLogger(WatermarkWorker.class);

    private final StripedExecutor executor;
    private final WatermarkProcessorService processor;
    private final MeterRegistry metrics;

    public WatermarkWorker(StripedExecutor executor, WatermarkProcessorService processor, MeterRegistry metrics) {
        this.executor = executor;
        this.processor = processor;
        this.metrics = metrics;
    }

    @KafkaListener(topics = DOCUMENT_WATERMARK_REQUEST_TOPIC, groupId = WATERMARK_WORKERS_GROUP)
    public void consume(WatermarkJobEvent event, Acknowledgment ack) {
        String key = event.getRequestId();
        executor.execute(key, () -> handleEvent(event, ack));
    }

    @Retry(name = "watermarkJob", fallbackMethod = "handleFailure")
    public void handleEvent(WatermarkJobEvent event, Acknowledgment ack) {
        try {
            log.info("Processing watermark [requestId={}, documentId={}]", event.getRequestId(), event.getDocumentId());
            processor.processWatermark(event, DOCUMENT_WATERMARK_PROCESSED_TOPIC);
            ack.acknowledge();
            metrics.counter("watermark_jobs_success").increment();
        } catch (Exception e) {
            log.error("Watermark processing failed: {}", e.getMessage(), e);
            metrics.counter("watermark_jobs_failed").increment();
            throw new RuntimeException(e);
        }
    }

    /** Fallback khi retry thất bại sau N lần */
    public void handleFailure(ConsumerRecord<String, String> record, Acknowledgment ack, Throwable ex) {
        log.error("Watermark job permanently failed after retries. key={}, error={}", record.key(), ex.getMessage());
        ack.acknowledge(); // tránh reconsume vô hạn
    }
}
