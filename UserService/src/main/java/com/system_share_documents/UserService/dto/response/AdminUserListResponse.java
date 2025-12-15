package com.system_share_documents.UserService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserListResponse {
    private List<AdminUserItem> content;
    private long totalElements;
    private int totalPages;
    private int number; // current page (0-indexed)
    private int size; // page size
    private boolean first;
    private boolean last;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AdminUserItem {
        private UUID id;
        private String username;
        private String email;
        private String fullName;
        private Short status; // 1 = active, 0 = disabled
        private Timestamp createdAt;
        private Timestamp updatedAt;
        private boolean hasKeys; // user có keys hay chưa
        private long keyCount; // số lượng keys của user
        private long groupCount; // số lượng groups user tham gia
    }
}
