package com.system_share_documents.UserService.controller.admin;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.response.DashboardStatsResponse;
import com.system_share_documents.UserService.service.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Chỉ admin mới được truy cập
public class AdminDashboardController {

    private final AdminStatisticsService adminStatisticsService;

    /**
     * Lấy thống kê tổng quan cho admin dashboard
     * GET /admin/dashboard/stats
     * 
     * @return DashboardStatsResponse chứa các thống kê về users, groups, keys, activities
     */
    @GetMapping("/stats")
    public ApiResponse<DashboardStatsResponse> getDashboardStats() {
        log.info("Admin dashboard stats requested");
        
        try {
            DashboardStatsResponse stats = adminStatisticsService.getDashboardStats();
            return ApiResponse.success("OK", "Dashboard statistics retrieved successfully", stats);
        } catch (Exception e) {
            log.error("Error retrieving dashboard statistics", e);
            return ApiResponse.error("ERROR", "Failed to retrieve dashboard statistics: " + e.getMessage());
        }
    }
}
