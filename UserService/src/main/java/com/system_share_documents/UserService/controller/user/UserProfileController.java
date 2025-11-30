package com.system_share_documents.UserService.controller.user;


import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.UpdateProfileRequest;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
            @RequestBody @Valid UpdateProfileRequest request,
            Authentication auth
    ) {
        UserResponse user = userProfileService.updateCurrentUserProfile(request, auth);
        return ApiResponse.success("OK", "Profile updated", user);
    }
}