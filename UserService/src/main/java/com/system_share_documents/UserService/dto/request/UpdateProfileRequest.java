package com.system_share_documents.UserService.dto.request;

import lombok.Data;

import java.util.Map;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private Map<String, Object> profile;
}