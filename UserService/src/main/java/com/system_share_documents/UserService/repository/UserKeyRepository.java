package com.system_share_documents.UserService.repository;

import com.system_share_documents.UserService.entity.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserKeyRepository extends JpaRepository<UserKey, UUID> {
    Optional<UserKey> findFirstByUser_IdAndIsPrimaryTrue(UUID userId);
}
