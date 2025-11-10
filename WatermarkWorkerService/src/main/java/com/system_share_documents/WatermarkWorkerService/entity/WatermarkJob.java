package com.system_share_documents.WatermarkWorkerService.entity;

import com.system_share_documents.WatermarkWorkerService.enums.WorkerProcessStatus;
import jakarta.persistence.*;
import lombok.*;
import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "watermark_jobs")
public class WatermarkJob {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(name = "recipient_user_id", nullable = false, length = 36)
    private String recipientUserId;

    @Column(name = "watermark_text", nullable = false, columnDefinition = "text")
    private String watermarkText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WorkerProcessStatus status;

    @Column(name = "generated_object_key", columnDefinition = "text")
    private String generatedObjectKey;

    @Column(name = "attempts")
    private Integer attempts;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage; // lưu nội dung lỗi nếu thất bại

    @Column(name = "processing_node", length = 128)
    private String processingNode; // tên máy xử lý (hostname)

    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "completed_at")
    private Timestamp completedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (attempts == null) attempts = 0;
        if (status == null) status = WorkerProcessStatus.PENDING;
        if (createdAt == null) createdAt = new Timestamp(System.currentTimeMillis());
    }
}
