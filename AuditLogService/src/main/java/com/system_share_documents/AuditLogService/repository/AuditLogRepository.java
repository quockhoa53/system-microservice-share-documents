package com.system_share_documents.AuditLogService.repository;


import com.system_share_documents.AuditLogService.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.sql.Timestamp;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(String userId);

    List<AuditLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            String userId,
            Timestamp from,
            Timestamp to
    );

    List<AuditLog> findByDocumentIdOrderByCreatedAtDesc(String documentId);
}