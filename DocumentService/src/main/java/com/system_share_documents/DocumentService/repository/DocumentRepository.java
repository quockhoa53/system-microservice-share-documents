package com.system_share_documents.DocumentService.repository;

import com.system_share_documents.DocumentService.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByIdIn(List<UUID> id);
    Page<Document> findAllByOwnerId(String ownerId, Pageable pageable);;
}
