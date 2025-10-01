package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.dto.response.UserKeyResponse_test;
import com.system_share_documents.UserService.entity.UserKey;
import com.system_share_documents.UserService.repository.UserKeyRepository;
import com.system_share_documents.UserService.service.UserKeyService_test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserKeyServiceImpl_test implements UserKeyService_test {

    @Autowired
    private UserKeyRepository userKeyRepository;

    @Override
    public UserKeyResponse_test getPrimaryPublicKey(UUID userId) {
        UserKey key = userKeyRepository.findFirstByUser_IdAndIsPrimaryTrue(userId)
                .orElseThrow(() -> new RuntimeException("Primary public key not found for user " + userId));

        return UserKeyResponse_test.builder()
                .publicKey(key.getPublicKey())
                .keyType(key.getKeyType())
                .isPrimary(key.getIsPrimary())
                .keyFingerprint(key.getKeyFingerprint())
                .build();
    }
}
