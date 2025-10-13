package com.system_share_documents.AppCommonService.kafka.producer;

import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.AUDIT_LOG_TOPIC;

@Component
public class AuditLogProducer {

    @Autowired
    private KafkaTemplate<String, AuditLogEvent> kafkaTemplate;

    public void sendAuditLog(AuditLogEvent event, String serviceName) {
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        kafkaTemplate.send(AUDIT_LOG_TOPIC, serviceName, event);
    }
}
