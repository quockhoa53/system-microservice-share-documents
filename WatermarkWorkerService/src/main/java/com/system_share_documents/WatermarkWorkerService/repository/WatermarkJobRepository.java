package com.system_share_documents.WatermarkWorkerService.repository;

import com.system_share_documents.WatermarkWorkerService.entity.WatermarkJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WatermarkJobRepository extends JpaRepository<WatermarkJob, String> {
    Optional<WatermarkJob> findTopByDocumentIdOrderByCreatedAtDesc(String documentId);
    Optional<WatermarkJob> findById(String requestId);
}

