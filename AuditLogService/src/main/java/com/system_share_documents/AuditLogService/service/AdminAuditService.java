package com.system_share_documents.AuditLogService.service;

import com.system_share_documents.AuditLogService.dto.response.AdminAuditLogListResponse;
import com.system_share_documents.AuditLogService.dto.response.AdminAuditStatsResponse;
import org.springframework.data.domain.Pageable;

public interface AdminAuditService {
    
    /**
     * Lấy danh sách audit logs với filters và pagination
     */
    AdminAuditLogListResponse getAllLogs(
            String userId,
            String action,
            String status,
            String objectType,
            String documentId,
            java.sql.Timestamp from,
            java.sql.Timestamp to,
            Pageable pageable
    );
    
    /**
     * Lấy thống kê audit logs
     */
    AdminAuditStatsResponse getAuditStats(
            String action,
            String status,
            java.sql.Timestamp from,
            java.sql.Timestamp to
    );
    
    /**
     * Export audit logs to CSV format
     */
    String exportLogsToCsv(
            String userId,
            String action,
            String status,
            String objectType,
            String documentId,
            java.sql.Timestamp from,
            java.sql.Timestamp to
    );
}
