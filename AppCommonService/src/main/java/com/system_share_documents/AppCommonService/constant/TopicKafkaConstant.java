package com.system_share_documents.AppCommonService.constant;

public class TopicKafkaConstant {
    private TopicKafkaConstant() {}

    public static final String AUDIT_LOG_TOPIC = "audit_log";
    public static final String DOCUMENT_WATERMARK_REQUEST_TOPIC = "document_watermark_request";
    public static final String DOCUMENT_WATERMARK_PROCESSED_TOPIC = "document_watermark_processed";
    public static final String DOCUMENT_WATERMARK_FAILED_TOPIC = "document_watermark_failed";
}
