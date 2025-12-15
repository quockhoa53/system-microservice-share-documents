package com.system_share_documents.UserService.repository;

import com.system_share_documents.UserService.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    boolean existsByOwner_IdAndNameIgnoreCase(UUID ownerId, String name);
    List<Group> findByOwner_Id(UUID ownerId);
    Optional<Group> findByKeycloakGroupId(String keycloakGroupId);
    
    // Statistics methods
    long count();
    
    @Query("SELECT COUNT(g) FROM Group g WHERE g.createdAt >= :from AND g.createdAt <= :to")
    long countByCreatedAtBetween(@Param("from") Timestamp from, @Param("to") Timestamp to);
}
