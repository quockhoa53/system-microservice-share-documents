package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.dto.request.AdminUpdateUserRequest;
import com.system_share_documents.UserService.dto.response.AdminUserDetailResponse;
import com.system_share_documents.UserService.dto.response.AdminUserListResponse;
import com.system_share_documents.UserService.entity.GroupMember;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.entity.UserKey;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.SystemError;
import com.system_share_documents.UserService.repository.GroupMemberRepository;
import com.system_share_documents.UserService.repository.UserKeyRepository;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final UserKeyRepository userKeyRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminUserListResponse getAllUsers(String search, Short status, Pageable pageable) {
        log.info("Admin: Get all users with search={}, status={}, page={}, size={}", 
                search, status, pageable.getPageNumber(), pageable.getPageSize());

        // Sanitize search string
        String searchTerm = (search != null && !search.trim().isEmpty()) ? search.trim() : null;

        Page<User> userPage = userRepository.findAllWithFilters(searchTerm, status, pageable);

        List<AdminUserListResponse.AdminUserItem> items = userPage.getContent().stream()
                .map(user -> {
                    UUID userId = user.getId();
                    
                    // Count keys
                    List<UserKey> userKeys = userKeyRepository.findByUser_Id(userId);
                    long keyCount = userKeys.size();
                    boolean hasKeys = keyCount > 0;
                    
                    // Count groups
                    List<GroupMember> memberships = groupMemberRepository.findByUser_Id(userId);
                    long groupCount = memberships.size();

                    return AdminUserListResponse.AdminUserItem.builder()
                            .id(userId)
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .fullName(user.getFullName())
                            .status(user.getStatus())
                            .createdAt(user.getCreatedAt())
                            .updatedAt(user.getUpdatedAt())
                            .hasKeys(hasKeys)
                            .keyCount(keyCount)
                            .groupCount(groupCount)
                            .build();
                })
                .collect(Collectors.toList());

        return AdminUserListResponse.builder()
                .content(items)
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .number(userPage.getNumber())
                .size(userPage.getSize())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        log.info("Admin: Get user detail for userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        // Get keys
        List<UserKey> userKeys = userKeyRepository.findByUser_Id(userId);
        List<AdminUserDetailResponse.UserKeyInfo> keyInfos = userKeys.stream()
                .map(key -> AdminUserDetailResponse.UserKeyInfo.builder()
                        .keyId(key.getId())
                        .keyType(key.getKeyType())
                        .isPrimary(key.getIsPrimary())
                        .createdAt(key.getCreatedAt())
                        .revokedAt(key.getRevokedAt())
                        .fingerprint(key.getKeyFingerprint())
                        .build())
                .collect(Collectors.toList());

        long activeKeys = userKeys.stream()
                .filter(key -> key.getRevokedAt() == null)
                .count();

        // Get groups
        List<GroupMember> memberships = groupMemberRepository.findByUser_Id(userId);
        List<AdminUserDetailResponse.GroupInfo> groupInfos = memberships.stream()
                .map(m -> AdminUserDetailResponse.GroupInfo.builder()
                        .groupId(m.getGroup().getId())
                        .groupName(m.getGroup().getName())
                        .role(m.getRole())
                        .joinedAt(m.getJoinedAt())
                        .build())
                .collect(Collectors.toList());

        return AdminUserDetailResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .keys(keyInfos)
                .totalKeys(userKeys.size())
                .activeKeys(activeKeys)
                .groups(groupInfos)
                .totalGroups(groupInfos.size())
                .build();
    }

    @Override
    @Transactional
    public AdminUserDetailResponse updateUser(UUID userId, AdminUpdateUserRequest request) {
        log.info("Admin: Update user userId={}, request={}", userId, request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        // Update fields
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getStatus() != null) {
            // Validate status
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new AppException(SystemError.INVALID_REQUEST, "Status must be 0 (disabled) or 1 (active)");
            }
            user.setStatus(request.getStatus());
        }
        user.setUpdatedAt(Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));

        User savedUser = userRepository.save(user);

        // TODO: Sync role với Keycloak nếu request.getRole() != null
        // Cần gọi Keycloak Admin API để update role

        log.info("Admin: User updated successfully userId={}", userId);

        // Return updated user detail
        return getUserDetail(userId);
    }

    @Override
    @Transactional
    public AdminUserDetailResponse updateUserStatus(UUID userId, Short status) {
        log.info("Admin: Update user status userId={}, status={}", userId, status);

        if (status != 0 && status != 1) {
            throw new AppException(SystemError.INVALID_REQUEST, "Status must be 0 (disabled) or 1 (active)");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        user.setStatus(status);
        user.setUpdatedAt(Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));
        userRepository.save(user);

        log.info("Admin: User status updated successfully userId={}, status={}", userId, status);

        return getUserDetail(userId);
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        log.info("Admin: Delete user (soft delete) userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        // Soft delete: set status to disabled
        user.setStatus((short) 0);
        user.setUpdatedAt(Timestamp.from(LocalDateTime.now().toInstant(ZoneOffset.UTC)));
        userRepository.save(user);

        log.info("Admin: User soft deleted successfully userId={}", userId);
        
        // TODO: Có thể cần disable user trong Keycloak nữa
    }
}
