package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.GroupDocument;
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
public interface GroupDocumentRepository extends JpaRepository<GroupDocument, UUID> {

    /**
     * Tìm tất cả GroupDocument theo documentId và chưa bị xóa
     */
    @Query("SELECT gd FROM GroupDocument gd WHERE gd.documentId = :documentId AND gd.deletedAt IS NULL")
    List<GroupDocument> findByDocumentIdAndNotDeleted(@Param("documentId") UUID documentId);

    /**
     * Tìm tất cả GroupDocument theo groupId và chưa bị xóa
     */
    @Query("SELECT gd FROM GroupDocument gd WHERE gd.groupId = :groupId AND gd.deletedAt IS NULL")
    Page<GroupDocument> findByGroupIdAndNotDeleted(@Param("groupId") String groupId, Pageable pageable);

    /**
     * Tìm GroupDocument theo documentId và groupId, chưa bị xóa
     */
    @Query("SELECT gd FROM GroupDocument gd WHERE gd.documentId = :documentId AND gd.groupId = :groupId AND gd.deletedAt IS NULL")
    Optional<GroupDocument> findByDocumentIdAndGroupIdAndNotDeleted(
            @Param("documentId") UUID documentId,
            @Param("groupId") String groupId
    );

    /**
     * Kiểm tra xem document đã được thêm vào group chưa (chưa bị xóa)
     */
    @Query("SELECT COUNT(gd) > 0 FROM GroupDocument gd WHERE gd.documentId = :documentId AND gd.groupId = :groupId AND gd.deletedAt IS NULL")
    boolean existsByDocumentIdAndGroupIdAndNotDeleted(
            @Param("documentId") UUID documentId,
            @Param("groupId") String groupId
    );

    /**
     * Đếm số lượng documents trong group (chưa bị xóa)
     */
    @Query("SELECT COUNT(gd) FROM GroupDocument gd WHERE gd.groupId = :groupId AND gd.deletedAt IS NULL")
    long countByGroupIdAndNotDeleted(@Param("groupId") String groupId);

    /**
     * Đếm số lượng groups chứa document (chưa bị xóa)
     */
    @Query("SELECT COUNT(gd) FROM GroupDocument gd WHERE gd.documentId = :documentId AND gd.deletedAt IS NULL")
    long countByDocumentIdAndNotDeleted(@Param("documentId") UUID documentId);

    /**
     * Soft delete: đánh dấu xóa GroupDocument
     */
    @Modifying
    @Transactional
    @Query("UPDATE GroupDocument gd SET gd.deletedAt = :deletedAt, gd.updatedAt = :updatedAt WHERE gd.id = :id")
    int softDeleteById(@Param("id") UUID id, @Param("deletedAt") Timestamp deletedAt, @Param("updatedAt") Timestamp updatedAt);

    /**
     * Soft delete: xóa document khỏi group
     */
    @Modifying
    @Transactional
    @Query("UPDATE GroupDocument gd SET gd.deletedAt = :deletedAt, gd.updatedAt = :updatedAt WHERE gd.documentId = :documentId AND gd.groupId = :groupId AND gd.deletedAt IS NULL")
    int softDeleteByDocumentIdAndGroupId(
            @Param("documentId") UUID documentId,
            @Param("groupId") String groupId,
            @Param("deletedAt") Timestamp deletedAt,
            @Param("updatedAt") Timestamp updatedAt
    );

    /**
     * Lấy danh sách group IDs chứa document (dùng để check membership qua UserService)
     */
    @Query("SELECT DISTINCT gd.groupId FROM GroupDocument gd WHERE gd.documentId = :documentId AND gd.deletedAt IS NULL")
    List<String> findGroupIdsByDocumentId(@Param("documentId") UUID documentId);

    /**
     * Lấy danh sách document IDs trong các groups (dùng để filter sau khi check membership)
     */
    @Query("SELECT DISTINCT gd.documentId FROM GroupDocument gd WHERE gd.groupId IN :groupIds AND gd.deletedAt IS NULL")
    List<UUID> findDocumentIdsByGroupIds(@Param("groupIds") List<String> groupIds);
}


