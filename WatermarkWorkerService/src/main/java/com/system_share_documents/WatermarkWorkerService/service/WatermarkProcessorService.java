package com.system_share_documents.WatermarkWorkerService.service;

import com.system_share_documents.AppCommonService.event.WatermarkJobEvent;

import java.util.concurrent.CompletableFuture;

public interface WatermarkProcessorService {
    CompletableFuture<Void> processWatermark(WatermarkJobEvent event, String topic);
}
