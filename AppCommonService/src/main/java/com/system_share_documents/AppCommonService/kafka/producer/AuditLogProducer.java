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

            log.debug("[AuditLogProducer] Sending audit log to topic {} with key: {}, action: {}", 
                    AUDIT_LOG_TOPIC, key, event.getAction());
            
            // Gửi trực tiếp, không cần CompletableFuture vì đã @Async
            kafkaTemplate.send(AUDIT_LOG_TOPIC, key, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[AuditLogProducer] Failed to send audit log for key {}: {}", key, ex.getMessage(), ex);
                        } else {
                            log.info("[AuditLogProducer] Audit log sent successfully - topic: {}, key: {}, action: {}", 
                                    AUDIT_LOG_TOPIC, key, event.getAction());
                        }
                    });

        } catch (Exception e) {
            log.error("[AuditLogProducer] Unexpected error when sending log async - key: {}, action: {}", 
                    key, event != null ? event.getAction() : "unknown", e);
        }
    }
}
