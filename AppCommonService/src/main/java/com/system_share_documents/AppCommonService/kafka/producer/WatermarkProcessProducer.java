package com.system_share_documents.AppCommonService.kafka.producer;

import com.system_share_documents.AppCommonService.event.WatermarkProcessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WatermarkProcessProducer {

    @Autowired
    private KafkaTemplate<String, WatermarkProcessEvent> kafkaTemplate;

    @Async
    public void sendWatermarkProcess(String topic, WatermarkProcessEvent event, String key) {
        kafkaTemplate.send(topic, key, event);
    }
}
