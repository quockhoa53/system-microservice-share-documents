package com.system_share_documents.UserService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {
    
    // User Statistics
    private UserStats userStats;
    
    // Group Statistics
    private GroupStats groupStats;
    
    // Key Statistics
    private KeyStats keyStats;
    
    // Activity Statistics
    private ActivityStats activityStats;
    
    // Top Users by Documents (from other services - có thể để null nếu chưa có)
    private List<TopUserStat> topUsersByDocuments;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserStats {
        private long totalUsers;
        private long activeUsers;
        private long inactiveUsers;
        private long newUsersLast24h;
        private long newUsersLast7Days;
        private long newUsersLast30Days;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupStats {
        private long totalGroups;
        private long newGroupsLast24h;
        private long newGroupsLast7Days;
        private long newGroupsLast30Days;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KeyStats {
        private long totalKeys;
        private long activeKeys;
        private long revokedKeys;
        private long usersWithKeys;
        private long usersWithoutKeys;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActivityStats {
        private long activitiesLast24h;
        private long activitiesLast7Days;
        private long activitiesLast30Days;
        
        // Breakdown by action type (from AuditLogService - có thể để null)
        private Map<String, Long> activitiesByTypeLast24h;
        private Map<String, Long> activitiesByTypeLast7Days;
        
        // Success/Failure rates
        private long successfulActionsLast24h;
        private long failedActionsLast24h;
        private double successRateLast24h; // percentage
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopUserStat {
        private String userId;
        private String username;
        private String fullName;
        private long documentCount; // sẽ được populate từ DocumentService nếu có
    }
}
