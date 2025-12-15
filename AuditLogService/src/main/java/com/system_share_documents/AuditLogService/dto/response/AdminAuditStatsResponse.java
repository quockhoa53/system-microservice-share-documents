package com.system_share_documents.AuditLogService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditStatsResponse {
    private Map<String, Long> countByAction; // action type -> count
    private Map<String, Long> countByStatus; // status -> count
    private Map<String, Long> countByObjectType; // objectType -> count
    private long totalLogs;
    private long successfulLogs;
    private long failedLogs;
    private double successRate; // percentage
}
