package com.system_share_documents.UserService.repository;

import com.system_share_documents.UserService.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {
    boolean existsByOwner_IdAndNameIgnoreCase(UUID ownerId, String name);
    List<Group> findByOwner_Id(UUID ownerId);
}
