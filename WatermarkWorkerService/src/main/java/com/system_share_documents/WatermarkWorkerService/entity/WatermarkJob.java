package com.system_share_documents.WatermarkWorkerService.entity;

import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "watermark_jobs")
public class WatermarkJob {

    @Id
    @GeneratedValue
    private String id; // định danh duy nhất cho job (UUID dưới dạng String)

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId; // ID tài liệu cần nhúng watermark

    @Column(name = "recipient_user_id", nullable = false, length = 36)
    private String recipientUserId; // ID người nhận (từ User Service)

    @Column(name = "watermark_text", nullable = false, columnDefinition = "text")
    private String watermarkText; // nội dung watermark (userID, timestamp,...)

    @Column(name = "status", nullable = false, length = 32)
    private String status = "pending"; // trạng thái job: pending, done, failed

    @Column(name = "generated_object_key", columnDefinition = "text")
    private String generatedObjectKey; // object key của file đã nhúng watermark

    @Column(name = "attempts")
    private Integer attempts = 0; // số lần retry nếu tạo watermark thất bại

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt; // thời điểm tạo job

    @Column(name = "completed_at")
    private Timestamp completedAt; // thời điểm hoàn tất job
}

