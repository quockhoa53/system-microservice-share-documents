package com.system_share_documents.AuthAccessControlService.repository;

import com.system_share_documents.AuthAccessControlService.entity.DocumentRecipient;
import com.system_share_documents.AuthAccessControlService.enums.DocumentAccessRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRecipientRepository extends JpaRepository<DocumentRecipient, UUID> {
    Optional<DocumentRecipient> findByDocumentIdAndRecipientUserId(String documentId, String recipientUserId);
    List<DocumentRecipient> findAllByDocumentId(String documentId);
    List<DocumentRecipient> findAllByDocumentIdAndAccessRole(String recipientUserId, DocumentAccessRole accessRole);
    @Modifying
    @Query("UPDATE DocumentRecipient dr SET dr.accessRole = :role, dr.isRevoke = true, dr.updatedAt = :updatedAt WHERE dr.documentId = :documentId AND dr.recipientUserId IN :userIds")
    void bulkRevokeAccess(@Param("documentId") String documentId,
                         @Param("userIds") List<String> userIds,
                         @Param("role") DocumentAccessRole role,
                         @Param("updatedAt") Timestamp updatedAt);
}
