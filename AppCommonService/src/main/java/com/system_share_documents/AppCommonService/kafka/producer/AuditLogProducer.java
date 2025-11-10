package com.system_share_documents.AppCommonService.kafka.producer;

import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.AUDIT_LOG_TOPIC;

@Slf4j
@Component
public class AuditLogProducer {

    @Autowired
    private KafkaTemplate<String, AuditLogEvent> kafkaTemplate;

    /**
     * Send audit log asynchronously using dedicated thread pool.
     * This is non-blocking and fault-tolerant.
     */
    @Async("auditExecutor")
    public void sendAuditLog(AuditLogEvent event, String key) {
        try {
            if (event.getTimestamp() == null) {
                event.setTimestamp(Instant.now());
            }

            CompletableFuture
                    .runAsync(() -> kafkaTemplate.send(AUDIT_LOG_TOPIC, key, event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("[AuditLogProducer] Failed to send audit log for key {}: {}", key, ex.getMessage());
                        } else if (log.isDebugEnabled()) {
                            log.debug("[AuditLogProducer] Audit log sent successfully: {}", key);
                        }
                    });

        } catch (Exception e) {
            log.error("[AuditLogProducer] Unexpected error when sending log async", e);
        }
    }
}
