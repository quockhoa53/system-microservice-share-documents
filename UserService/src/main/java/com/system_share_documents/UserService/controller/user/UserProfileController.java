package com.system_share_documents.UserService.controller.user;


import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.UpdateProfileRequest;
import com.system_share_documents.UserService.dto.response.UserBrief;
import com.system_share_documents.UserService.dto.response.UserResponse;
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

    // GET /api/users/search – tìm kiếm users
    @GetMapping("/search")
    public ApiResponse<List<UserBrief>> searchUsers(@RequestParam String query) {
        List<UserBrief> users = userProfileService.searchUsers(query);
        return ApiResponse.success("OK", "Users found", users);
    }
}