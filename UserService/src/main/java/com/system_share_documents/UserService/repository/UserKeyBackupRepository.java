package com.system_share_documents.UserService.repository;

import com.system_share_documents.UserService.entity.UserKeyBackup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserKeyBackupRepository extends JpaRepository<UserKeyBackup, UUID> {
    Optional<UserKeyBackup> findByUser_Id(UUID userId);
    boolean existsByUser_Id(UUID userId);
}
