package com.system_share_documents.AuditLogService.controller;


import com.system_share_documents.AuditLogService.dto.request.CreateAuditLogRequest;
import com.system_share_documents.AuditLogService.dto.response.AuditLogResponse;
import com.system_share_documents.AuditLogService.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit")
@RequiredArgsConstructor
public class InternalAuditController {

    private final AuditLogService auditLogService;

    // POST /internal/audit/logs
    @PostMapping("/logs")
    public AuditLogResponse createLog(@RequestBody CreateAuditLogRequest request) {
        // internal: có thể không bọc ApiResponse, trả thẳng JSON cho microservice khác
        return auditLogService.createLog(request);
    }
}