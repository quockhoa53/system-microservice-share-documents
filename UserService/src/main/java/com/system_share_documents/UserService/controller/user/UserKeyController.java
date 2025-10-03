package com.system_share_documents.UserService.controller.user;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.UploadKeyRequest;
import com.system_share_documents.UserService.dto.response.PublicKeyResponse;
import com.system_share_documents.UserService.service.UserKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/keys")
@RequiredArgsConstructor
public class UserKeyController {

    private final UserKeyService userKeyService;

    @GetMapping("/me")
    public ApiResponse<List<PublicKeyResponse>> myKeys(Authentication auth) {
        List<PublicKeyResponse> keys = userKeyService.getMyKeys(auth);
        return ApiResponse.success("OK", "My public keys", keys);
    }

    @PostMapping
    public ApiResponse<Void> upload(@RequestBody UploadKeyRequest request, Authentication auth) {
        userKeyService.uploadPublicKeys(request, auth);
        return ApiResponse.success("OK", "Keys uploaded", null);
    }

    @GetMapping("/{userId}")
    public ApiResponse<List<PublicKeyResponse>> keysOfUser(@PathVariable UUID userId, Authentication auth) {
        List<PublicKeyResponse> keys = userKeyService.getKeysOfUser(userId, auth);
        return ApiResponse.success("OK", "User public keys", keys);
    }

    @PostMapping("/{keyId}/primary")
    public ApiResponse<Void> setPrimary(@PathVariable UUID keyId, Authentication auth) {
        userKeyService.setPrimary(keyId, auth);
        return ApiResponse.success("OK", "Primary set", null);
    }

    @PostMapping("/{keyId}/revoke")
    public ApiResponse<Void> revoke(@PathVariable UUID keyId, Authentication auth) {
        userKeyService.revokeKey(keyId, auth);
        return ApiResponse.success("OK", "Key revoked", null);
    }

}
