package com.system_share_documents.UserService.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String avatar; // URL của ảnh đại diện (upload từ Cloudinary)
    private Map<String, Object> profile;
}