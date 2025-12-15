package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.request.UpdateProfileRequest;
import com.system_share_documents.UserService.dto.response.InternalUserInfoResponse;
import com.system_share_documents.UserService.dto.response.UserBrief;
import com.system_share_documents.UserService.dto.response.UserResponse;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

public interface UserProfileService {

    UserResponse getCurrentUser(Authentication auth);

    UserResponse updateCurrentUserProfile(UpdateProfileRequest request, Authentication auth);

    List<UserBrief> searchUsers(String query);

    InternalUserInfoResponse getInternalUserById(UUID userId);

    InternalUserInfoResponse getInternalUserByUsername(String username);
}