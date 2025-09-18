package com.system_share_documents.APIGatewayService.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "request_log")
public class RequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String method;
    private String path;
    private int status;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "service_name")
    private String serviceName;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;
}
