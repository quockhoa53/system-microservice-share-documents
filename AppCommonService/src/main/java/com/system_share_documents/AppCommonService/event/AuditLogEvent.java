package com.system_share_documents.AppCommonService.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEvent {
    private String userId;
    private String action;
    private String documentId;
    private String objectType;
    private String status;
    private String ip;
    private String userAgent;
    private String request;
    private String metadata;
    private Instant timestamp;
}
