package com.system_share_documents.AuditLogService.controller;


import com.system_share_documents.AuditLogService.dto.ApiResponse;
import com.system_share_documents.AuditLogService.dto.response.AuditLogResponse;
import com.system_share_documents.AuditLogService.service.AuditLogService;
import com.system_share_documents.AuditLogService.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogService auditLogService;

    /**
     * Lấy audit logs của user hiện tại (tự động lấy userId từ JWT token)
     * @param auth Authentication từ Spring Security
     * @param from Ngày bắt đầu (optional)
     * @param to Ngày kết thúc (optional)
     * @param action Filter theo action (optional)
     * @return Danh sách audit logs
     */
    @GetMapping("/my")
    public ApiResponse<List<AuditLogResponse>> myLogs(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String action
    ) {
        String userId = SecurityUtils.requireCurrentUserId(auth);
        
        Timestamp fromTs = from != null ? Timestamp.from(from.toInstant(ZoneOffset.UTC)) : null;
        Timestamp toTs = to != null ? Timestamp.from(to.toInstant(ZoneOffset.UTC)) : null;
        
        List<AuditLogResponse> logs = auditLogService.getLogsOfUser(userId, fromTs, toTs);
        
        // Filter theo action nếu có
        if (action != null && !action.isBlank()) {
            logs = logs.stream()
                    .filter(log -> action.equalsIgnoreCase(log.getAction()))
                    .toList();
        }
        
        return ApiResponse.success("OK", "My audit logs", logs);
    }

    /**
     * Lấy audit logs theo document
     */
    @GetMapping("/documents/{documentId}")
    public ApiResponse<List<AuditLogResponse>> logsByDocument(
            @PathVariable String documentId
    ) {
        var logs = auditLogService.getLogsOfDocument(documentId);
        return ApiResponse.success("OK", "Document audit logs", logs);
    }

    /**
     * Xem chi tiết một log
     */
    @GetMapping("/{id}")
    public ApiResponse<AuditLogResponse> getLogDetail(@PathVariable Long id) {
        var log = auditLogService.getLogDetail(id);
        return ApiResponse.success("OK", "Audit log detail", log);
    }
}