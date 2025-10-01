package com.system_share_documents.UserService.controller.user;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.response.UserKeyResponse_test;
import com.system_share_documents.UserService.service.UserKeyService_test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserKeyController_test {

    @Autowired
    private UserKeyService_test userKeyService;

    /**
     * Lấy public key primary của user (theo userId)
     */
    @GetMapping("/get/public-key/{userId}")
    public ApiResponse<UserKeyResponse_test> getPrimaryPublicKey(@PathVariable UUID userId) {
        UserKeyResponse_test response = userKeyService.getPrimaryPublicKey(userId);
        return ApiResponse.success("OK", "Get public key success", response);
    }
}
