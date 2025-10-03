package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.request.UploadKeyRequest;
import com.system_share_documents.UserService.dto.response.PublicKeyResponse;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

public interface UserKeyService {
    List<PublicKeyResponse> getMyKeys(Authentication auth);
    void uploadPublicKeys(UploadKeyRequest request, Authentication auth);
    List<PublicKeyResponse> getKeysOfUser(UUID userId, Authentication auth);
    void setPrimary(UUID keyId, Authentication auth);
    void revokeKey(UUID keyId, Authentication auth);
}
