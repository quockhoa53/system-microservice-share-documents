package com.system_share_documents.AuditLogService.config.kafka;

import com.system_share_documents.AuditLogService.dto.request.CreateAuditLogRequest;
import com.system_share_documents.AuditLogService.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.AUDIT_LOG_TOPIC;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogKafkaConsumer {

    private final AuditLogService auditLogService;

    @KafkaListener(
            topics = AUDIT_LOG_TOPIC,
            groupId = "audit-log-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleAuditLogEvent(CreateAuditLogRequest event) {
        try {
            log.debug("[AuditLogConsumer] Received audit log event - userId: {}, action: {}", 
                    event.getUserId(), event.getAction());
            auditLogService.createLog(event);
            log.debug("[AuditLogConsumer] Successfully processed audit log - userId: {}, action: {}", 
                    event.getUserId(), event.getAction());
        } catch (Exception e) {
            // Log lỗi nhưng không throw để không làm chết consumer
            log.error("[AuditLogConsumer] Failed to process audit log - userId: {}, action: {}, error: {}", 
                    event != null ? event.getUserId() : "unknown", 
                    event != null ? event.getAction() : "unknown", 
                    e.getMessage(), e);
        }
    }
}
