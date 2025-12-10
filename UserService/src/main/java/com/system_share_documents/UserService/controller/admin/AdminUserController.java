package com.system_share_documents.UserService.controller.admin;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.AdminUpdateUserRequest;
import com.system_share_documents.UserService.dto.response.AdminUserDetailResponse;
import com.system_share_documents.UserService.dto.response.AdminUserListResponse;
import com.system_share_documents.UserService.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * Lấy danh sách users với pagination và filters
     * GET /admin/users?search=keyword&status=1&page=0&size=10&sort=createdAt,desc
     */
    @GetMapping
    public ApiResponse<AdminUserListResponse> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Short status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        log.info("Admin: Get all users - search={}, status={}, page={}, size={}, sort={}", 
                search, status, page, size, sort);

        // Parse sort parameter (format: "field,direction" or just "field")
        String[] sortParts = sort.split(",");
        String sortField = sortParts[0];
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        AdminUserListResponse response = adminUserService.getAllUsers(search, status, pageable);
        return ApiResponse.success("OK", "Users retrieved successfully", response);
    }

    /**
     * Lấy chi tiết user
     * GET /admin/users/{userId}
     */
    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> getUserDetail(@PathVariable UUID userId) {
        log.info("Admin: Get user detail - userId={}", userId);

        AdminUserDetailResponse response = adminUserService.getUserDetail(userId);
        return ApiResponse.success("OK", "User detail retrieved successfully", response);
    }

    /**
     * Cập nhật thông tin user
     * PUT /admin/users/{userId}
     */
    @PutMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> updateUser(
            @PathVariable UUID userId,
            @RequestBody AdminUpdateUserRequest request
    ) {
        log.info("Admin: Update user - userId={}, request={}", userId, request);

        AdminUserDetailResponse response = adminUserService.updateUser(userId, request);
        return ApiResponse.success("OK", "User updated successfully", response);
    }

    /**
     * Cập nhật trạng thái user (activate/deactivate)
     * PATCH /admin/users/{userId}/status
     */
    @PatchMapping("/{userId}/status")
    public ApiResponse<AdminUserDetailResponse> updateUserStatus(
            @PathVariable UUID userId,
            @RequestParam Short status
    ) {
        log.info("Admin: Update user status - userId={}, status={}", userId, status);

        AdminUserDetailResponse response = adminUserService.updateUserStatus(userId, status);
        return ApiResponse.success("OK", "User status updated successfully", response);
    }

    /**
     * Xóa user (soft delete)
     * DELETE /admin/users/{userId}
     */
    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(@PathVariable UUID userId) {
        log.info("Admin: Delete user - userId={}", userId);

        adminUserService.deleteUser(userId);
        return ApiResponse.success("OK", "User deleted successfully", null);
    }
}
