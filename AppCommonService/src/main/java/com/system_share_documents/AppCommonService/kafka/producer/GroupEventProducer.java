package com.system_share_documents.AppCommonService.kafka.producer;

//import com.system_share_documents.AppCommonService.event.MemberJoinedGroupEvent;
import com.system_share_documents.AppCommonService.event.MemberJoinedGroupEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.MEMBER_JOINED_GROUP_TOPIC;

/**
 * Producer để publish events liên quan đến group
 */
@Slf4j
@Component
public class GroupEventProducer {

    @Autowired
    private KafkaTemplate<String, MemberJoinedGroupEvent> kafkaTemplateMemberJoinedGroup;

    /**
     * Publish event khi member mới join group
     * Flink job sẽ consume event này để mã hóa CEK cho member mới
     */
    @Async
    public void publishMemberJoinedGroup(String groupId, String userId, String role, String addedBy) {
        try {
            MemberJoinedGroupEvent event = MemberJoinedGroupEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .groupId(groupId)
                    .userId(userId)
                    .role(role)
                    .addedBy(addedBy)
                    .timestamp(Instant.now())
                    .build();

            log.info("[GroupEventProducer] Publishing MemberJoinedGroupEvent - groupId: {}, userId: {}, role: {}",
                    groupId, userId, role);

            // Key = groupId để đảm bảo events của cùng group được xử lý theo thứ tự
            kafkaTemplateMemberJoinedGroup.send(MEMBER_JOINED_GROUP_TOPIC, groupId, event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[GroupEventProducer] Failed to send MemberJoinedGroupEvent - groupId: {}, userId: {}, error: {}",
                                    groupId, userId, ex.getMessage(), ex);
                        } else {
                            log.info("[GroupEventProducer] MemberJoinedGroupEvent sent successfully - groupId: {}, userId: {}",
                                    groupId, userId);
                        }
                    });
        } catch (Exception e) {
            log.error("[GroupEventProducer] Unexpected error when sending MemberJoinedGroupEvent - groupId: {}, userId: {}",
                    groupId, userId, e);
        }
    }
}