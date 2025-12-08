package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.DocumentKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentKeyRepository extends JpaRepository<DocumentKey, UUID> {
    Optional<DocumentKey> findByDocumentVersionIdAndRecipientId(UUID versionId, String recipientId);
    Boolean existsByDocumentVersionIdAndRecipientId(UUID versionId, String recipientId);
    List<DocumentKey> findByRecipientId(String recipientId);
}
