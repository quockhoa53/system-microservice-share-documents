package com.system_share_documents.AppCommonService.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event được publish khi một member mới join group
 * Flink job sẽ consume event này để mã hóa CEK cho member mới với tất cả documents trong group
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberJoinedGroupEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId; // UUID để track event
    private String groupId; // UUID của group
    private String userId; // UUID của user mới join
    private String role; // Role của user trong group: "owner", "admin", "member"
    private Instant timestamp; // Thời điểm join
    private String addedBy; // UUID của user thêm member vào group
}