package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.request.AdminUpdateUserRequest;
import com.system_share_documents.UserService.dto.response.AdminUserDetailResponse;
import com.system_share_documents.UserService.dto.response.AdminUserListResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminUserService {
    
    /**
     * Lấy danh sách users với pagination và filters
     */
    AdminUserListResponse getAllUsers(String search, Short status, Pageable pageable);
    
    /**
     * Lấy chi tiết user
     */
    AdminUserDetailResponse getUserDetail(java.util.UUID userId);
    
    /**
     * Cập nhật thông tin user (fullName, status)
     */
    AdminUserDetailResponse updateUser(java.util.UUID userId, AdminUpdateUserRequest request);
    
    /**
     * Vô hiệu hóa/kích hoạt user
     */
    AdminUserDetailResponse updateUserStatus(java.util.UUID userId, Short status);
    
    /**
     * Xóa user (soft delete - set status = 0)
     */
    void deleteUser(java.util.UUID userId);
}
