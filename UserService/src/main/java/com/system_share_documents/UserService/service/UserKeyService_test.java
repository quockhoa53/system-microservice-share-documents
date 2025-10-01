package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.response.UserKeyResponse_test;

import java.util.UUID;

public interface UserKeyService_test {
    UserKeyResponse_test getPrimaryPublicKey(UUID userId);
}
