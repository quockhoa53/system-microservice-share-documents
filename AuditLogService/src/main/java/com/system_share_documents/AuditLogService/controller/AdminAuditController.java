package com.system_share_documents.AuditLogService.controller;

import com.system_share_documents.AuditLogService.dto.ApiResponse;
import com.system_share_documents.AuditLogService.dto.response.AdminAuditLogListResponse;
import com.system_share_documents.AuditLogService.dto.response.AdminAuditStatsResponse;
import com.system_share_documents.AuditLogService.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    /**
     * Lấy danh sách audit logs với filters và pagination
     * GET /admin/audit/logs?userId=xxx&action=LOGIN&status=OK&page=0&size=10&sort=createdAt,desc
     */
    @GetMapping("/logs")
    public ApiResponse<AdminAuditLogListResponse> getAllLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        log.info("Admin: Get all audit logs - userId={}, action={}, status={}, from={}, to={}, page={}, size={}", 
                userId, action, status, from, to, page, size);

        // Convert LocalDateTime to Timestamp
        Timestamp fromTs = from != null ? Timestamp.from(from.toInstant(ZoneOffset.UTC)) : null;
        Timestamp toTs = to != null ? Timestamp.from(to.toInstant(ZoneOffset.UTC)) : null;

        // Parse sort parameter
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        AdminAuditLogListResponse response = adminAuditService.getAllLogs(
                userId, action, status, objectType, documentId, fromTs, toTs, pageable
        );

        return ApiResponse.success("OK", "Audit logs retrieved successfully", response);
    }

    /**
     * Lấy thống kê audit logs
     * GET /admin/audit/stats?action=LOGIN&status=OK&from=2024-01-01T00:00:00&to=2024-01-31T23:59:59
     */
    @GetMapping("/stats")
    public ApiResponse<AdminAuditStatsResponse> getAuditStats(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        log.info("Admin: Get audit stats - action={}, status={}, from={}, to={}", action, status, from, to);

        Timestamp fromTs = from != null ? Timestamp.from(from.toInstant(ZoneOffset.UTC)) : null;
        Timestamp toTs = to != null ? Timestamp.from(to.toInstant(ZoneOffset.UTC)) : null;

        AdminAuditStatsResponse response = adminAuditService.getAuditStats(action, status, fromTs, toTs);
        return ApiResponse.success("OK", "Audit statistics retrieved successfully", response);
    }

    /**
     * Export audit logs to CSV
     * GET /admin/audit/export/csv?userId=xxx&action=LOGIN&status=OK&from=2024-01-01T00:00:00&to=2024-01-31T23:59:59
     */
    @GetMapping("/export/csv")
    public ResponseEntity<String> exportLogsToCsv(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        log.info("Admin: Export audit logs to CSV - userId={}, action={}, status={}, from={}, to={}", 
                userId, action, status, from, to);

        Timestamp fromTs = from != null ? Timestamp.from(from.toInstant(ZoneOffset.UTC)) : null;
        Timestamp toTs = to != null ? Timestamp.from(to.toInstant(ZoneOffset.UTC)) : null;

        String csvContent = adminAuditService.exportLogsToCsv(
                userId, action, status, objectType, documentId, fromTs, toTs
        );

        // Generate filename with timestamp
        String filename = "audit_logs_" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent);
    }
}
