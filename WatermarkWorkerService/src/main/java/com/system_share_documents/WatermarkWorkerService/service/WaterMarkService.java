package com.system_share_documents.WatermarkWorkerService.service;

public interface WaterMarkService {
    byte[] addWatermark(byte[] input, String text);
}
