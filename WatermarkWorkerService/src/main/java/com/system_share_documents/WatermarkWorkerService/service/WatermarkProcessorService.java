package com.system_share_documents.WatermarkWorkerService.service;

import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;

public interface WatermarkProcessorService {
    void processWatermark(WatermarkJobEvent event, String topic) throws Exception;
}
