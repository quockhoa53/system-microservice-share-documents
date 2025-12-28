package com.system_share_documents.DocumentService.kafka.consume;

import com.system_share_documents.AppCommonService.event.MemberJoinedGroupEvent;
import com.system_share_documents.DocumentService.service.MemberDocumentKeyEncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import static com.system_share_documents.AppCommonService.constant.GroupIdKafkaConstant.DOCUMENT_SERVICE_GROUP;
import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.MEMBER_JOINED_GROUP_TOPIC;

/**
 * Kafka consumer để xử lý event khi member mới join group
 * Tự động mã hóa CEK cho member mới với tất cả documents trong group
 */
@Component
public class MemberJoinedGroupEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(MemberJoinedGroupEventConsumer.class);

    @Autowired
    private MemberDocumentKeyEncryptionService encryptionService;

    @KafkaListener(
            topics = MEMBER_JOINED_GROUP_TOPIC,
            groupId = DOCUMENT_SERVICE_GROUP,
            containerFactory = "memberJoinedGroupKafkaListenerContainerFactory"
    )
    public void consume(MemberJoinedGroupEvent event, Acknowledgment ack) {
        String requestId = event.getRequestId();
        log.info("[requestId={}] Consumed MemberJoinedGroupEvent - groupId: {}, userId: {}, role: {}",
                requestId, event.getGroupId(), event.getUserId(), event.getRole());

        try {
            encryptionService.encryptCEKForNewMember(event);
            log.info("[requestId={}] Successfully encrypted CEK for new member - groupId: {}, userId: {}",
                    requestId, event.getGroupId(), event.getUserId());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[requestId={}] Failed to encrypt CEK for new member - groupId: {}, userId: {}, error: {}",
                    requestId, event.getGroupId(), event.getUserId(), e.getMessage(), e);
            // Acknowledge để tránh infinite retry - log lỗi và tiếp tục
            // Có thể implement dead letter queue nếu cần
            ack.acknowledge();
        }
    }
}


