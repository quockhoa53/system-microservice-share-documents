package com.system_share_documents.AuditLogService.repository;


import com.system_share_documents.AuditLogService.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    
    // Admin methods - Advanced filtering
    // Sử dụng native query với COALESCE để tránh lỗi PostgreSQL parameter type inference
    // Note: Không dùng Pageable.sort trong native query, sort được xử lý trong service
    @Query(value = "SELECT * FROM audit_logs al WHERE " +
           "(COALESCE(:userId, '') = '' OR al.user_id = :userId) AND " +
           "(COALESCE(:action, '') = '' OR al.action = :action) AND " +
           "(COALESCE(:status, '') = '' OR al.status = :status) AND " +
           "(COALESCE(:objectType, '') = '' OR al.object_type = :objectType) AND " +
           "(COALESCE(:documentId, '') = '' OR al.document_id = :documentId) AND " +
           "al.created_at >= COALESCE(:from, al.created_at) AND " +
           "al.created_at <= COALESCE(:to, al.created_at) " +
           "ORDER BY al.created_at DESC " +
           "LIMIT :limit OFFSET :offset",
           nativeQuery = true)
    List<AuditLog> findAllWithFilters(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("status") String status,
            @Param("objectType") String objectType,
            @Param("documentId") String documentId,
            @Param("from") Timestamp from,
            @Param("to") Timestamp to,
            @Param("limit") int limit,
            @Param("offset") int offset
    );
    
    // Count query riêng - sử dụng COALESCE để tránh lỗi PostgreSQL parameter type inference
    @Query(value = "SELECT COUNT(*) FROM audit_logs al WHERE " +
            "(COALESCE(:userId, '') = '' OR al.user_id = :userId) AND " +
            "(COALESCE(:action, '') = '' OR al.action = :action) AND " +
            "(COALESCE(:status, '') = '' OR al.status = :status) AND " +
            "(COALESCE(:objectType, '') = '' OR al.object_type = :objectType) AND " +
            "(COALESCE(:documentId, '') = '' OR al.document_id = :documentId) AND " +
            "al.created_at >= COALESCE(:from, al.created_at) AND " +
            "al.created_at <= COALESCE(:to, al.created_at)",
            nativeQuery = true)
    long countAllWithFilters(
            @Param("userId") String userId,
            @Param("action") String action,
            @Param("status") String status,
            @Param("objectType") String objectType,
            @Param("documentId") String documentId,
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );


    // Statistics queries
    @Query(value = "SELECT COUNT(*) FROM audit_logs al WHERE " +
            "(COALESCE(:action, '') = '' OR al.action = :action) AND " +
            "(COALESCE(:status, '') = '' OR al.status = :status) AND " +
            "al.created_at >= COALESCE(:from, al.created_at) AND " +
            "al.created_at <= COALESCE(:to, al.created_at)",
            nativeQuery = true)
    long countWithFilters(
            @Param("action") String action,
            @Param("status") String status,
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );


    @Query(value = "SELECT al.action, COUNT(*) FROM audit_logs al WHERE " +
            "al.created_at >= COALESCE(:from, al.created_at) AND " +
            "al.created_at <= COALESCE(:to, al.created_at) " +
           "GROUP BY al.action",
           nativeQuery = true)
    List<Object[]> countByAction(
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );
    
    @Query(value = "SELECT al.status, COUNT(*) FROM audit_logs al WHERE " +
            "al.created_at >= COALESCE(:from, al.created_at) AND " +
            "al.created_at <= COALESCE(:to, al.created_at) " +
           "GROUP BY al.status",
           nativeQuery = true)
    List<Object[]> countByStatus(
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );
}