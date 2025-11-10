package com.system_share_documents.AppCommonService.kafka.producer;

import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import static com.system_share_documents.AppCommonService.constant.TopicKafkaConstant.DOCUMENT_WATERMARK_REQUEST_TOPIC;

@Component
public class WatermarkJobProducer {

    @Autowired
    private KafkaTemplate<String, WatermarkJobEvent> kafkaTemplate;

    @Async
    public void sendWatermarkJob(WatermarkJobEvent event, String key) {
        kafkaTemplate.send(DOCUMENT_WATERMARK_REQUEST_TOPIC, key, event);
    }
}
