package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.response.DashboardStatsResponse;
import com.system_share_documents.UserService.repository.GroupMemberRepository;
import com.system_share_documents.UserService.repository.GroupRepository;
import com.system_share_documents.UserService.repository.UserKeyRepository;
import com.system_share_documents.UserService.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.system_share_documents.UserService.constant.UserStatus.ACTIVE;
import static com.system_share_documents.UserService.constant.UserStatus.DISABLED;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final UserKeyRepository userKeyRepository;
    private final GroupMemberRepository groupMemberRepository;

    /**
     * Lấy tổng quan thống kê cho admin dashboard
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        log.info("Calculating admin dashboard statistics");

        // Tính toán time ranges
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);
        LocalDateTime last7Days = now.minusDays(7);
        LocalDateTime last30Days = now.minusDays(30);

        Timestamp nowTs = Timestamp.from(now.toInstant(ZoneOffset.UTC));
        Timestamp last24hTs = Timestamp.from(last24h.toInstant(ZoneOffset.UTC));
        Timestamp last7DaysTs = Timestamp.from(last7Days.toInstant(ZoneOffset.UTC));
        Timestamp last30DaysTs = Timestamp.from(last30Days.toInstant(ZoneOffset.UTC));

        // User Statistics
        DashboardStatsResponse.UserStats userStats = calculateUserStats(
                last24hTs, last7DaysTs, last30DaysTs
        );

        // Group Statistics
        DashboardStatsResponse.GroupStats groupStats = calculateGroupStats(
                last24hTs, last7DaysTs, last30DaysTs
        );

        // Key Statistics
        DashboardStatsResponse.KeyStats keyStats = calculateKeyStats();

        // Activity Statistics (from AuditLogService - placeholder for now)
        DashboardStatsResponse.ActivityStats activityStats = calculateActivityStats(
                last24hTs, last7DaysTs, last30DaysTs
        );

        // Top Users (from DocumentService - placeholder for now)
        List<DashboardStatsResponse.TopUserStat> topUsers = new ArrayList<>();
        // TODO: Call DocumentService to get top users by document count

        return DashboardStatsResponse.builder()
                .userStats(userStats)
                .groupStats(groupStats)
                .keyStats(keyStats)
                .activityStats(activityStats)
                .topUsersByDocuments(topUsers)
                .build();
    }

    private DashboardStatsResponse.UserStats calculateUserStats(
            Timestamp last24h, Timestamp last7Days, Timestamp last30Days) {
        
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(ACTIVE);
        long inactiveUsers = userRepository.countByStatus(DISABLED);
        
        // New users in time ranges
        Timestamp now = Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC));
        long newUsersLast24h = userRepository.countByCreatedAtBetween(last24h, now);
        long newUsersLast7Days = userRepository.countByCreatedAtBetween(last7Days, now);
        long newUsersLast30Days = userRepository.countByCreatedAtBetween(last30Days, now);

        return DashboardStatsResponse.UserStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .inactiveUsers(inactiveUsers)
                .newUsersLast24h(newUsersLast24h)
                .newUsersLast7Days(newUsersLast7Days)
                .newUsersLast30Days(newUsersLast30Days)
                .build();
    }

    private DashboardStatsResponse.GroupStats calculateGroupStats(
            Timestamp last24h, Timestamp last7Days, Timestamp last30Days) {
        
        long totalGroups = groupRepository.count();
        
        Timestamp now = Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC));
        long newGroupsLast24h = groupRepository.countByCreatedAtBetween(last24h, now);
        long newGroupsLast7Days = groupRepository.countByCreatedAtBetween(last7Days, now);
        long newGroupsLast30Days = groupRepository.countByCreatedAtBetween(last30Days, now);

        return DashboardStatsResponse.GroupStats.builder()
                .totalGroups(totalGroups)
                .newGroupsLast24h(newGroupsLast24h)
                .newGroupsLast7Days(newGroupsLast7Days)
                .newGroupsLast30Days(newGroupsLast30Days)
                .build();
    }

    private DashboardStatsResponse.KeyStats calculateKeyStats() {
        long totalKeys = userKeyRepository.count();
        long activeKeys = userKeyRepository.countByRevokedAtIsNull();
        long revokedKeys = totalKeys - activeKeys;
        long usersWithKeys = userKeyRepository.countDistinctUsersWithKeys();
        
        // Users without keys = total users - users with keys
        long totalUsers = userRepository.count();
        long usersWithoutKeys = Math.max(0, totalUsers - usersWithKeys);

        return DashboardStatsResponse.KeyStats.builder()
                .totalKeys(totalKeys)
                .activeKeys(activeKeys)
                .revokedKeys(revokedKeys)
                .usersWithKeys(usersWithKeys)
                .usersWithoutKeys(usersWithoutKeys)
                .build();
    }

    /**
     * Calculate activity statistics
     * Note: This is a placeholder. In production, this should call AuditLogService
     * to get actual activity data. For now, returning empty stats.
     */
    private DashboardStatsResponse.ActivityStats calculateActivityStats(
            Timestamp last24h, Timestamp last7Days, Timestamp last30Days) {
        
        // TODO: Call AuditLogService to get actual activity counts
        // For now, return placeholder values
        return DashboardStatsResponse.ActivityStats.builder()
                .activitiesLast24h(0L)
                .activitiesLast7Days(0L)
                .activitiesLast30Days(0L)
                .activitiesByTypeLast24h(new HashMap<>())
                .activitiesByTypeLast7Days(new HashMap<>())
                .successfulActionsLast24h(0L)
                .failedActionsLast24h(0L)
                .successRateLast24h(0.0)
                .build();
    }
}
