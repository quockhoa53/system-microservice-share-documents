package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByIdIn(List<UUID> id);

    @Query("SELECT d FROM Document d WHERE d.ownerId = :ownerId AND d.deletedAt IS NULL")
    Page<Document> findActiveDocumentsByOwnerId(@Param("ownerId") String ownerId, Pageable pageable);

    @Query(value = "SELECT get_shared_documents(:userId, :limit, :createdAt, :docId)", nativeQuery = true)
    String getSharedDocuments(
            @Param("userId") String userId,
            @Param("limit") Integer limit,
            @Param("createdAt") Timestamp createdAt,
            @Param("docId") UUID docId
    );

    @Query("SELECT d FROM Document d WHERE d.id = :documentId AND d.deletedAt IS NULL")
    Optional<Document> findByIdAndNotDeleted(@Param("documentId") UUID documentId);
}
