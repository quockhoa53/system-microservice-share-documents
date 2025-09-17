package com.system_share_documents.UserService.controller;

import com.system_share_documents.UserService.entity.User;

import com.system_share_documents.UserService.service.UserProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class MeController {
    private final UserProvisioningService provisioning;

    @GetMapping("/me")
    public User me(Authentication auth) {
        return provisioning.ensureUser(auth); // trả về record trong DB (đã được tạo nếu chưa có)
    }
}