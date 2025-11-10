package com.system_share_documents.WatermarkWorkerService.repository;

import com.system_share_documents.WatermarkWorkerService.entity.WatermarkJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatermarkJobRepository extends JpaRepository<WatermarkJob, String> {
}
