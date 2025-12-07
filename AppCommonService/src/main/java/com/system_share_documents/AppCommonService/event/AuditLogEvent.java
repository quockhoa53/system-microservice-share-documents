package com.system_share_documents.AppCommonService.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private String requestId;
    private String userId;
    private String affectedUsers;
    private String action;
    private String documentId;
    private String objectType;
    private String typeLog;
    private String status;
    private String errorReason;
    private String ip;
    private String userAgent;
    private String request;
    private String metadata;
    private Instant timestamp;
}
