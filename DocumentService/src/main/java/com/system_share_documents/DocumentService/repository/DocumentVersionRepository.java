package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {
    Optional<DocumentVersion> findByDocumentAndVersionNumber(Document doc, Integer versionNumber);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM DocumentVersion v WHERE v.document.id = :documentId")
    Integer findLatestVersionNumber(UUID documentId);

    @Query(value = "SELECT * FROM get_document_versions(CAST(:documentId AS uuid), CAST(:statusList AS TEXT[]), :limit, :offset)", nativeQuery = true)
    List<Object[]> getDocumentVersions(
            @Param("documentId") String documentId,
            @Param("statusList") String[] statusList,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

}
