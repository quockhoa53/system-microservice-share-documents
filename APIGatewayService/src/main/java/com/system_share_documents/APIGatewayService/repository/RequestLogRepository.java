package com.system_share_documents.APIGatewayService.repository;

import com.system_share_documents.APIGatewayService.entity.RequestLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {
}
