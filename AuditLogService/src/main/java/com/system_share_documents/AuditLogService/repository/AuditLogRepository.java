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

    @Query(value = "SELECT * FROM sp_find_by_user_id(:userId)", nativeQuery = true)
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    @Query(value = "SELECT * FROM sp_find_by_user_id_and_date_range(:userId, :from, :to)", nativeQuery = true)
    List<AuditLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("userId") String userId,
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );

    @Query(value = "SELECT * FROM sp_find_by_document_id(:documentId)", nativeQuery = true)
    List<AuditLog> findByDocumentIdOrderByCreatedAtDesc(@Param("documentId") String documentId);
    
    // Admin methods - Advanced filtering
    @Query(value = "SELECT * FROM sp_find_all_with_filters(" +
           ":userId, :action, :status, :objectType, :documentId, :from, :to, :limit, :offset)",
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

    @Query(value = "SELECT sp_count_all_with_filters(" +
            ":userId, :action, :status, :objectType, :documentId, :from, :to)",
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


    @Query(value = "SELECT sp_count_with_filters(:action, :status, :from, :to)", nativeQuery = true)
    long countWithFilters(
            @Param("action") String action,
            @Param("status") String status,
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );


    @Query(value = "SELECT action, count FROM sp_count_by_action(:from, :to)", nativeQuery = true)
    List<Object[]> countByAction(
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );

    @Query(value = "SELECT status, count FROM sp_count_by_status(:from, :to)", nativeQuery = true)
    List<Object[]> countByStatus(
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );
}