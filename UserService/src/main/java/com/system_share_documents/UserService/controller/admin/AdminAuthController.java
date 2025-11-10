package com.system_share_documents.UserService.controller.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminAuthController {
    @PreAuthorize("hasRole('ADMIN')") // từ realm role/ client role ROLE_ADMIN
    @GetMapping("/stats")
    public String stats() {
        return "ok";
    }
}