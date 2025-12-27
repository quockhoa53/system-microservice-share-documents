package com.system_share_documents.AppCommonService.constant;

public class TopicKafkaConstant {
    private TopicKafkaConstant() {}

    public static final String AUDIT_LOG_TOPIC = "audit_log";
    public static final String DOCUMENT_WATERMARK_REQUEST_TOPIC = "document_watermark_request";
    public static final String DOCUMENT_WATERMARK_PROCESSED_TOPIC = "document_watermark_processed";
    public static final String DOCUMENT_WATERMARK_FAILED_TOPIC = "document_watermark_failed";
    public static final String MEMBER_JOINED_GROUP_TOPIC = "member_joined_group";
    public static final String MALWARE_SCAN_REQUEST_TOPIC = "malware_scan_request";
    public static final String MALWARE_SCAN_RESULT_TOPIC = "malware_scan_result";
}
