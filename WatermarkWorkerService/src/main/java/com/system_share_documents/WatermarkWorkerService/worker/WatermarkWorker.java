package com.system_share_documents.WatermarkWorkerService.worker;

import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import com.system_share_documents.WatermarkWorkerService.concurrent.StripedExecutor;
import com.system_share_documents.WatermarkWorkerService.service.WatermarkProcessorService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.system_share_documents.AppCommonService.constant.GroupIdKafkaConstant.WATERMARK_WORKERS_GROUP;
import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.DOCUMENT_WATERMARK_PROCESSED_TOPIC;
import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.DOCUMENT_WATERMARK_REQUEST_TOPIC;

@Component
public class WatermarkWorker {
    private static final Logger log = LoggerFactory.getLogger(WatermarkWorker.class);

    private final StripedExecutor executor;
    private final WatermarkProcessorService processor;
    private final MeterRegistry metrics;

    public WatermarkWorker(StripedExecutor executor,
                           WatermarkProcessorService processor,
                           MeterRegistry metrics) {
        this.executor = executor;
        this.processor = processor;
        this.metrics = metrics;
    }

    @KafkaListener(topics = DOCUMENT_WATERMARK_REQUEST_TOPIC, groupId = WATERMARK_WORKERS_GROUP)
    public void consume(WatermarkJobEvent event, Acknowledgment ack) {
        executor.execute(event.getRequestId(), () ->
                handleAsync(event)
                        .whenComplete((r, ex) -> {
                            if (ex == null) {
                                ack.acknowledge();
                                metrics.counter("watermark_jobs_success").increment();
                            } else {
                                log.error("Watermark permanently failed → moving on. requestId={}, error={}",
                                        event.getRequestId(), ex.getMessage(), ex);
                                metrics.counter("watermark_jobs_failed").increment();

                                // Bạn có thể push DLQ ở đây nếu muốn
                                ack.acknowledge();
                            }
                        })
        );
    }

    /**
     * Handler chính — luôn trả CompletableFuture
     */
    private CompletableFuture<Void> handleAsync(WatermarkJobEvent event) {
        log.info("Start watermark [requestId={}, documentId={}]", event.getRequestId(), event.getDocumentId());
        return processor.processWatermark(event, DOCUMENT_WATERMARK_PROCESSED_TOPIC);
    }
}

