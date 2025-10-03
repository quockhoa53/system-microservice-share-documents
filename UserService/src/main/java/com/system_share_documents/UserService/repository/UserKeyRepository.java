package com.system_share_documents.UserService.repository;

import com.system_share_documents.UserService.entity.UserKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserKeyRepository extends JpaRepository<UserKey, UUID> {
    List<UserKey> findByUser_Id(UUID userId);
    Optional<UserKey> findByUser_IdAndIsPrimaryTrue(UUID userId);
    boolean existsByUser_IdAndKeyFingerprint(UUID userId, String fpActual);
}