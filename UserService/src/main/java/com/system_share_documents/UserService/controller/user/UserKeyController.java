package com.system_share_documents.UserService.controller.user;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.GetPublicKeyRequest;
import com.system_share_documents.UserService.dto.request.UploadKeyRequest;
import com.system_share_documents.UserService.dto.response.PublicKeyResponse;
import com.system_share_documents.UserService.service.UserKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
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

    @GetMapping("/public/{userId}")
    public ApiResponse<List<PublicKeyResponse>> publicKeysById(@PathVariable UUID userId) {
        var keys = userKeyService.getPublicKeysOfUser(userId);
        return ApiResponse.success("OK", "User public keys", keys);
    }

    @PostMapping("/public-key/get")
    public ApiResponse<Optional<PublicKeyResponse>> getPublicKeysByUserId(@RequestBody GetPublicKeyRequest request) {
        var keys = userKeyService.getPublicEncryptionKey(request);
        return ApiResponse.success("OK", "Get public key for user successfully", keys);
    }

    @GetMapping("/public/{userId}/primary")
    public ApiResponse<PublicKeyResponse> publicPrimaryById(
            @PathVariable UUID userId,
            @RequestParam String keyType
    ) {
        return userKeyService.getPublicPrimaryKey(userId, keyType)
                .map(k -> ApiResponse.success("OK", "Primary public key", k))
                .orElseGet(() -> ApiResponse.success("OK", "No primary key", null));
    }

    @GetMapping("/public/by-username/{username}")
    public ApiResponse<List<PublicKeyResponse>> publicKeysByUsername(@PathVariable String username) {
        var keys = userKeyService.getPublicKeysByUsername(username);
        return ApiResponse.success("OK", "User public keys by username", keys);
    }

    @GetMapping("/public/by-username/{username}/primary")
    public ApiResponse<PublicKeyResponse> publicPrimaryByUsername(
            @PathVariable String username,
            @RequestParam String keyType
    ) {
        return userKeyService.getPublicPrimaryKeyByUsername(username, keyType)
                .map(k -> ApiResponse.success("OK", "Primary public key", k))
                .orElseGet(() -> ApiResponse.success("OK", "No primary key", null));
    }

}
