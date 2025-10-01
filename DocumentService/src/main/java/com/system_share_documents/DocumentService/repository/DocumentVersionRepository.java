package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    Optional<DocumentVersion> findByDocumentAndVersionNumber(Document doc, Integer versionNumber);
}
