package com.system_share_documents.UserService.controller.user;


import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.ChangePasswordRequest;
import com.system_share_documents.UserService.dto.request.UpdateProfileRequest;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.service.SearchUserService;
import com.system_share_documents.UserService.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    private final SearchUserService searchUserService;

    // GET /api/users/me – lấy thông tin user hiện tại
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(Authentication auth) {
        UserResponse user = userProfileService.getCurrentUser(auth);
        return ApiResponse.success("OK", "Current user", user);
    }

    // PATCH /api/users/me – cập nhật fullName + profile
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMe(
            @RequestBody UpdateProfileRequest request,
            Authentication auth
    ) {
        UserResponse user = userProfileService.updateCurrentUserProfile(request, auth);
        return ApiResponse.success("OK", "Profile updated", user);
    }

    // PUT /api/users/me/password – đổi password
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication auth
    ) {
        userProfileService.changePassword(request, auth);
        return ApiResponse.success("OK", "Password changed successfully", null);
    }

    @GetMapping("/search")
    public ApiResponse<List<UserResponse>> searchUsers(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit
    ) {
        List<UserResponse> users = searchUserService.searchUsers(query, limit);
        return ApiResponse.success("OK", "Search completed", users);
    }
}