package com.system_share_documents.AuditLogService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLogListResponse {
    private List<AuditLogResponse> content;
    private long totalElements;
    private int totalPages;
    private int number; // current page (0-indexed)
    private int size; // page size
    private boolean first;
    private boolean last;
    
    // Statistics for the filtered results
    private AuditStats stats;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AuditStats {
        private Map<String, Long> countByAction; // action -> count
        private Map<String, Long> countByStatus; // status -> count
        private long totalActions;
        private long successfulActions;
        private long failedActions;
        private double successRate; // percentage
    }
}
