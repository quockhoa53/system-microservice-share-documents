package com.system_share_documents.AppCommonService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO cho user cache data từ Redis
 * Được dùng bởi tất cả services để đọc user info từ shared cache
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCacheResponse {
    private String id;
    private String username;
    private String email;
    private String full_name; // Redis key là full_name (snake_case)
    private String status;
    private String created_at;
    private String updated_at;
    private String profile; // JSON string từ Redis

    // Helper methods
    public String getFullName() {
        return full_name;
    }

    public Short getStatusAsShort() {
        try {
            return status != null ? Short.parseShort(status) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}