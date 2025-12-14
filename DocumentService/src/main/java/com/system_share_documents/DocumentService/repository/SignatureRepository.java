package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.Signature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SignatureRepository extends JpaRepository<Signature, UUID> {
    List<Signature> findByDocumentId(UUID documentId);
}

