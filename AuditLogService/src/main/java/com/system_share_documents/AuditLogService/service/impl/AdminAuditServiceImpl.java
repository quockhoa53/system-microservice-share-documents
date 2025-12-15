package com.system_share_documents.AuditLogService.service.impl;

import com.system_share_documents.AuditLogService.dto.response.AdminAuditLogListResponse;
import com.system_share_documents.AuditLogService.dto.response.AdminAuditStatsResponse;
import com.system_share_documents.AuditLogService.dto.response.AuditLogResponse;
import com.system_share_documents.AuditLogService.entity.AuditLog;
import com.system_share_documents.AuditLogService.repository.AuditLogRepository;
import com.system_share_documents.AuditLogService.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminAuditLogListResponse getAllLogs(
            String userId,
            String action,
            String status,
            String objectType,
            String documentId,
            Timestamp from,
            Timestamp to,
            Pageable pageable
    ) {
        log.info("Admin: Get all audit logs with filters - userId={}, action={}, status={}, from={}, to={}, page={}, size={}", 
                userId, action, status, from, to, pageable.getPageNumber(), pageable.getPageSize());

        // Get total count first
        long totalElements = auditLogRepository.countAllWithFilters(
                userId, action, status, objectType, documentId, from, to
        );

        // Calculate pagination
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int offset = page * size;
        int totalPages = (int) Math.ceil((double) totalElements / size);

        // Get paginated results
        List<AuditLog> logs = auditLogRepository.findAllWithFilters(
                userId, action, status, objectType, documentId, from, to, size, offset
        );

        // Convert to response
        List<AuditLogResponse> content = logs.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Calculate statistics for filtered results
        AdminAuditLogListResponse.AuditStats stats = calculateStats(action, status, from, to);

        return AdminAuditLogListResponse.builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .number(page)
                .size(size)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .stats(stats)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAuditStatsResponse getAuditStats(
            String action,
            String status,
            Timestamp from,
            Timestamp to
    ) {
        log.info("Admin: Get audit stats - action={}, status={}, from={}, to={}", action, status, from, to);

        // Get count by action
        List<Object[]> actionCounts = auditLogRepository.countByAction(from, to);
        Map<String, Long> countByAction = new HashMap<>();
        for (Object[] row : actionCounts) {
            String act = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            countByAction.put(act, count);
        }

        // Get count by status
        List<Object[]> statusCounts = auditLogRepository.countByStatus(from, to);
        Map<String, Long> countByStatus = new HashMap<>();
        for (Object[] row : statusCounts) {
            String st = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            countByStatus.put(st, count);
        }

        // Calculate totals
        long totalLogs = auditLogRepository.countWithFilters(action, status, from, to);
        long successfulLogs = countByStatus.getOrDefault("OK", 0L) + 
                              countByStatus.getOrDefault("SUCCESS", 0L);
        long failedLogs = countByStatus.getOrDefault("FAIL", 0L) + 
                          countByStatus.getOrDefault("FAILURE", 0L);
        double successRate = totalLogs > 0 
                ? (double) successfulLogs / totalLogs * 100.0 
                : 0.0;

        // Count by object type (simplified - you may want to add a query for this)
        Map<String, Long> countByObjectType = new HashMap<>(); // TODO: Add query if needed

        return AdminAuditStatsResponse.builder()
                .countByAction(countByAction)
                .countByStatus(countByStatus)
                .countByObjectType(countByObjectType)
                .totalLogs(totalLogs)
                .successfulLogs(successfulLogs)
                .failedLogs(failedLogs)
                .successRate(successRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String exportLogsToCsv(
            String userId,
            String action,
            String status,
            String objectType,
            String documentId,
            Timestamp from,
            Timestamp to
    ) {
        log.info("Admin: Export audit logs to CSV with filters");

        // Get all logs (without pagination for export)
        // Note: This might be slow for large datasets, consider adding a limit
        // Using a large limit (10000) to get most records, adjust as needed
        int exportLimit = 10000;
        List<AuditLog> logs = auditLogRepository.findAllWithFilters(
                userId, action, status, objectType, documentId, from, to, exportLimit, 0
        );

        // Build CSV
        StringBuilder csv = new StringBuilder();
        // CSV Header
        csv.append("ID,User ID,Action,Status,Object Type,Document ID,IP,User Agent,Created At\n");

        // CSV Rows
        for (AuditLog log : logs) {
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,\"%s\",%s\n",
                    log.getId(),
                    escapeCsv(log.getUserId()),
                    escapeCsv(log.getAction()),
                    escapeCsv(log.getStatus()),
                    escapeCsv(log.getObjectType()),
                    escapeCsv(log.getDocumentId()),
                    escapeCsv(log.getIp()),
                    escapeCsv(log.getUserAgent()),
                    log.getCreatedAt()
            ));
        }

        return csv.toString();
    }

    private AdminAuditLogListResponse.AuditStats calculateStats(
            String action,
            String status,
            Timestamp from,
            Timestamp to
    ) {
        // Get count by action
        List<Object[]> actionCounts = auditLogRepository.countByAction(from, to);
        Map<String, Long> countByAction = new HashMap<>();
        for (Object[] row : actionCounts) {
            String act = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            countByAction.put(act, count);
        }

        // Get count by status
        List<Object[]> statusCounts = auditLogRepository.countByStatus(from, to);
        Map<String, Long> countByStatus = new HashMap<>();
        for (Object[] row : statusCounts) {
            String st = (String) row[0];
            Long count = ((Number) row[1]).longValue();
            countByStatus.put(st, count);
        }

        // Calculate totals
        long totalActions = auditLogRepository.countWithFilters(action, status, from, to);
        long successfulActions = countByStatus.getOrDefault("OK", 0L) + 
                                countByStatus.getOrDefault("SUCCESS", 0L);
        long failedActions = countByStatus.getOrDefault("FAIL", 0L) + 
                            countByStatus.getOrDefault("FAILURE", 0L);
        double successRate = totalActions > 0 
                ? (double) successfulActions / totalActions * 100.0 
                : 0.0;

        return AdminAuditLogListResponse.AuditStats.builder()
                .countByAction(countByAction)
                .countByStatus(countByStatus)
                .totalActions(totalActions)
                .successfulActions(successfulActions)
                .failedActions(failedActions)
                .successRate(successRate)
                .build();
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .affectedUsers(log.getAffectedUsers())
                .action(log.getAction())
                .documentId(log.getDocumentId())
                .objectType(log.getObjectType())
                .typeLog(log.getTypeLog())
                .status(log.getStatus())
                .ip(log.getIp())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .metadata(log.getMetadata())
                .build();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains comma or quote
        if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
