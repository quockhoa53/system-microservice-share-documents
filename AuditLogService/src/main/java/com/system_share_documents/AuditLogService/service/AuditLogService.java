package com.system_share_documents.AuditLogService.service;

import com.system_share_documents.AuditLogService.dto.request.CreateAuditLogRequest;
import com.system_share_documents.AuditLogService.dto.response.AuditLogResponse;

import java.sql.Timestamp;
import java.util.List;

public interface AuditLogService {

    // Internal: các service khác gọi để ghi log
    AuditLogResponse createLog(CreateAuditLogRequest request);

    // Public: lấy log của user hiện tại (UserId truyền vào hoặc lấy từ auth gateway)
    List<AuditLogResponse> getLogsOfUser(String userId, Timestamp from, Timestamp to);

    // Public: log của 1 document (cho UI xem lịch sử)
    List<AuditLogResponse> getLogsOfDocument(String documentId);

    // Public/internal: xem chi tiết 1 log
    AuditLogResponse getLogDetail(Long id);
}