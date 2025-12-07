package com.system_share_documents.AuditLogService.controller;


import com.system_share_documents.AuditLogService.dto.ApiResponse;
import com.system_share_documents.AuditLogService.dto.response.AuditLogResponse;
import com.system_share_documents.AuditLogService.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    @GetMapping("/my")
    public ApiResponse<List<AuditLogResponse>> myLogs(
            @RequestParam String userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        Timestamp fromTs = Timestamp.from(from.toInstant(ZoneOffset.UTC));
        Timestamp toTs   = Timestamp.from(to.toInstant(ZoneOffset.UTC));
        var logs = auditLogService.getLogsOfUser(userId, fromTs, toTs);
        return ApiResponse.success("OK", "My audit logs", logs);
    }


    // 2) Log theo document
    @GetMapping("/documents/{documentId}")
    public ApiResponse<List<AuditLogResponse>> logsByDocument(
            @PathVariable String documentId
    ) {
        var logs = auditLogService.getLogsOfDocument(documentId);
        return ApiResponse.success("OK", "Document audit logs", logs);
    }

    // 3) Xem chi tiết một log
    @GetMapping("/{id}")
    public ApiResponse<AuditLogResponse> getLogDetail(@PathVariable Long id) {
        var log = auditLogService.getLogDetail(id);
        return ApiResponse.success("OK", "Audit log detail", log);
    }
}