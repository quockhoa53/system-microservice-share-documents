package com.system_share_documents.UserService.controller.user;


import com.system_share_documents.UserService.dto.response.InternalUserInfoResponse;
import com.system_share_documents.UserService.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserProfileService userProfileService;

    // GET /internal/users/{userId}
    @GetMapping("/{userId}")
    public InternalUserInfoResponse getUserById(@PathVariable UUID userId) {
        return userProfileService.getInternalUserById(userId);
    }

    // GET /internal/users/by-username/{username}
    @GetMapping("/by-username/{username}")
    public InternalUserInfoResponse getUserByUsername(@PathVariable String username) {
        return userProfileService.getInternalUserByUsername(username);
    }
}