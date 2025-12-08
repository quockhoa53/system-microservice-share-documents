package com.system_share_documents.AuditLogService.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.system_share_documents.AuditLogService.dto.request.CreateAuditLogRequest;
import com.system_share_documents.AuditLogService.dto.response.AuditLogResponse;
import com.system_share_documents.AuditLogService.entity.AuditLog;
import com.system_share_documents.AuditLogService.exception.AppException;
import com.system_share_documents.AuditLogService.exception.errorcode.SystemError;
import com.system_share_documents.AuditLogService.repository.AuditLogRepository;
import com.system_share_documents.AuditLogService.service.AuditLogService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public AuditLogResponse createLog(CreateAuditLogRequest req) {
        // Metadata đã là JSON string từ Kafka hoặc REST API, không cần convert
        String metadataJson = req.getMetadata();

        AuditLog log = AuditLog.builder()
                .userId(req.getUserId())
                .affectedUsers(req.getAffectedUsers())
                .action(req.getAction())
                .documentId(req.getDocumentId())
                .objectType(req.getObjectType())
                .status(req.getStatus())
                .errorReason(req.getErrorReason())
                .ip(req.getIp())
                .userAgent(req.getUserAgent())
                .request(req.getRequest())
                .metadata(metadataJson)
                .typeLog(req.getTypeLog() != null ? req.getTypeLog() : "USER")
                .createdAt(Timestamp.from(Instant.now()))
                .build();


        AuditLog saved = auditLogRepository.save(log);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public List<AuditLogResponse> getLogsOfUser(String userId, Timestamp from, Timestamp to) {
        List<AuditLog> logs;
        if (from != null && to != null) {
            logs = auditLogRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, from, to);
        } else {
            logs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        return logs.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public List<AuditLogResponse> getLogsOfDocument(String documentId) {
        List<AuditLog> logs = auditLogRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        return logs.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public AuditLogResponse getLogDetail(Long id) {
        AuditLog log = auditLogRepository.findById(id)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "Audit log not found"));
        return toResponse(log);
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
}