package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
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
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM DocumentVersion v WHERE v.document.id = :documentId")
    Integer findLatestVersionNumber(UUID documentId);

    @Query(value = "SELECT * FROM get_document_versions(CAST(:documentId AS uuid), CAST(:statusList AS TEXT[]), :limit, :offset)", nativeQuery = true)
    List<Object[]> getDocumentVersions(
            @Param("documentId") String documentId,
            @Param("statusList") String[] statusList,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId AND v.id IN :versionIds AND v.deletedAt IS NULL")
    List<DocumentVersion> findAllByDocumentIdAndIdInAndNotDeleted(@Param("documentId") UUID documentId, @Param("versionIds") List<UUID> versionIds);

    @Modifying
    @Transactional
    @Query("UPDATE DocumentVersion v SET v.deletedAt = :deletedAt WHERE v.id IN :versionIds AND v.deletedAt IS NULL")
    int batchSoftDeleteByIds(@Param("versionIds") List<UUID> versionIds, @Param("deletedAt") Timestamp deletedAt);

    @Query("SELECT COUNT(v) FROM DocumentVersion v WHERE v.document.id = :documentId AND v.deletedAt IS NULL") long countByDocumentIdAndDeletedAtIsNull(@Param("documentId") UUID documentId);

    @Modifying
    @Transactional
    @Query("UPDATE DocumentVersion v SET v.deletedAt = :deletedAt WHERE v.document.id = :documentId AND v.deletedAt IS NULL")
    int batchSoftDeleteByDocumentId(@Param("documentId") UUID documentId, @Param("deletedAt") Timestamp deletedAt);

    @Query("SELECT v FROM DocumentVersion v WHERE v.document.id = :documentId AND v.deletedAt IS NULL ORDER BY v.versionNumber DESC")
    Optional<DocumentVersion> findLatestVersionByDocumentId(@Param("documentId") UUID documentId);

    @Query("SELECT v.id, v.wrappedCEKMaster FROM DocumentVersion v WHERE v.id IN :versionIds AND v.deletedAt IS NULL")
    List<Object[]> findWrappedCEKMasterByIds(@Param("versionIds") List<UUID> versionIds);
}
