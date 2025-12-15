package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.dto.request.AddMemberRequest;
import com.system_share_documents.UserService.dto.request.CreateGroupRequest;
import com.system_share_documents.UserService.dto.response.GroupDetailResponse;
import com.system_share_documents.UserService.dto.response.GroupMemberResponse;
import com.system_share_documents.UserService.dto.response.GroupResponse;
import com.system_share_documents.UserService.dto.response.InternalMembershipResponse;
import com.system_share_documents.UserService.entity.Group;
import com.system_share_documents.UserService.entity.GroupMember;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.AuthError;
import com.system_share_documents.UserService.exception.errorcode.SystemError;
import com.system_share_documents.UserService.mapper.GroupMapper;
import com.system_share_documents.UserService.repository.GroupMemberRepository;
import com.system_share_documents.UserService.repository.GroupRepository;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.CacheService;
import com.system_share_documents.UserService.service.GroupService;
import com.system_share_documents.UserService.utils.SecurityUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMapper groupMapper;
    private final com.system_share_documents.UserService.service.KeycloakGroupService keycloakGroupService;
    private final CacheService cacheService;
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    @Qualifier("redisObjectMapper")
    private ObjectMapper redisObjectMapper;

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, Authentication auth) {
        // Lấy user hiện tại từ Authentication (giống UserKeyServiceImpl)
        UUID ownerId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new AppException(SystemError.INTERNAL_ERROR, "Owner not found"));

        // Validate name
        String name = request.getName();
        if (name == null || name.isBlank()) {
            throw new AppException(SystemError.INTERNAL_ERROR, "Group name is required");
        }

        // Option: chặn trùng tên trong phạm vi owner
        if (groupRepository.existsByOwner_IdAndNameIgnoreCase(ownerId, name)) {
            throw new AppException(SystemError.INTERNAL_ERROR, "Group name already exists for this owner");
        }

        Short visibility = request.getVisibility();
        if (visibility == null) visibility = 0; // default private

        Timestamp now = new Timestamp(System.currentTimeMillis());

        // 1. Tạo group trong Keycloak trước
        org.keycloak.representations.idm.GroupRepresentation kcGroup = null;
        try {
            kcGroup = keycloakGroupService.createKeycloakGroup(name, request.getDescription());
            // 2. Thêm owner vào Keycloak group
            keycloakGroupService.addUserToKeycloakGroup(kcGroup.getId(), ownerId);
        } catch (Exception e) {
            log.warn("Failed to create group in Keycloak, continuing with database only: {}", e.getMessage());
            // Tiếp tục tạo trong database nếu Keycloak fail
        }

        // 3. Tạo Group trong database
        Group group = Group.builder()
                .name(name)
                .description(request.getDescription())
                .visibility(visibility)
                .owner(owner)
                .keycloakGroupId(kcGroup != null ? kcGroup.getId() : null)
                .createdAt(now)
                .build();
        Group savedGroup = groupRepository.save(group);

        // 4. Tạo GroupMember cho owner với role "owner"
        GroupMember gm = GroupMember.builder()
                .group(savedGroup)
                .user(owner)
                .role("owner")
                .joinedAt(now)
                .build();
        groupMemberRepository.save(gm);

        // Evict cache
        cacheService.evictUserGroupsCache(ownerId);

        // Map sang response
        GroupResponse response = groupMapper.toResponse(savedGroup);
        long memberCount = groupMemberRepository.countByGroup_Id(savedGroup.getId());
        response.setMemberCount(memberCount);

        return response;
    }


    @Override
    @Transactional
    public List<GroupResponse> getMyGroups(Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);
        
        // Check cache first - use RedisTemplate directly
        try {
            if (redisTemplate != null && redisObjectMapper != null) {
                String cacheKey = "userGroups::" + currentUserId.toString();
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (cached instanceof String) {
                        // Deserialize from JSON string
                        List<GroupResponse> result = redisObjectMapper.readValue(
                            (String) cached,
                            new TypeReference<List<GroupResponse>>() {}
                        );
                        log.debug("Cache HIT for userGroups: userId={}", currentUserId);
                        return result;
                    } else if (cached instanceof List) {
                        log.debug("Cache HIT for userGroups: userId={}", currentUserId);
                        return (List<GroupResponse>) cached;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Cache read error, loading from database: {}", e.getMessage());
        }
        
        log.debug("Cache MISS for userGroups: userId={}, loading from database", currentUserId);
        
        // Cache miss - load from database
        // Optimize: fetch all memberships with groups in one query
        var memberships = groupMemberRepository.findByUser_Id(currentUserId);
        
        // Batch load member counts for all groups at once to avoid N+1
        List<UUID> groupIds = memberships.stream()
                .map(m -> m.getGroup().getId())
                .toList();
        
        // Use a map to store member counts
        java.util.Map<UUID, Long> memberCountMap = new java.util.HashMap<>();
        for (UUID groupId : groupIds) {
            memberCountMap.put(groupId, groupMemberRepository.countByGroup_Id(groupId));
        }
        
        List<GroupResponse> response = memberships.stream()
                .map(m -> {
                    Group g = m.getGroup();
                    long memberCount = memberCountMap.getOrDefault(g.getId(), 0L);
                    return toGroupResponse(g, memberCount);
                })
                .toList();
        
        // Store in cache - use RedisTemplate directly to serialize manually
        try {
            if (redisTemplate != null && redisObjectMapper != null) {
                String cacheKey = "userGroups::" + currentUserId.toString();
                // Serialize to JSON string with proper type information
                String json = redisObjectMapper.writerFor(new TypeReference<List<GroupResponse>>() {})
                    .writeValueAsString(response);
                
                // Store as string in cache with TTL
                redisTemplate.opsForValue().set(cacheKey, json, java.time.Duration.ofMinutes(30));
                log.debug("Cache stored successfully for userGroups: userId={}, groups count={}", currentUserId, response.size());
            } else {
                log.warn("RedisTemplate or redisObjectMapper is null");
            }
        } catch (Exception e) {
            log.warn("Cache write error, continuing without cache: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.error("Cache write error details", e);
            }
        }
        
        return response;
    }

    @Override
    @Transactional
    public GroupDetailResponse getGroupDetail(UUID groupId, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "Group not found"));

        // Kiểm tra quyền xem: private thì phải là member, internal/public thì tuỳ bạn (ở đây allow mọi user login)
        if (group.getVisibility() == 0) { // private
            var membershipOpt = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, currentUserId);
            if (membershipOpt.isEmpty()) {
                throw new AppException(AuthError.FORBIDDEN, "You are not a member of this private group");
            }
        }

        long memberCount = groupMemberRepository.countByGroup_Id(groupId);
        var myMembership = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, currentUserId);

        String myRole = myMembership.map(GroupMember::getRole).orElse(null);

        return GroupDetailResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .visibility(group.getVisibility())
                .ownerId(group.getOwner() != null ? group.getOwner().getId() : null)
                .createdAt(group.getCreatedAt())
                .memberCount(memberCount)
                .myRole(myRole)
                .build();
    }

    @Override
    @Transactional
    public List<GroupMemberResponse> listMembers(UUID groupId, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "Group not found"));

        // Rule: chỉ member mới được xem danh sách thành viên (bạn có thể relax rule này nếu group public)
        var myMembership = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, currentUserId);
        if (myMembership.isEmpty()) {
            throw new AppException(AuthError.FORBIDDEN, "You are not a member of this group");
        }

        var members = groupMemberRepository.findByGroup_Id(groupId);

        return members.stream()
                .map(m -> {
                    User u = m.getUser();
                    return GroupMemberResponse.builder()
                            .userId(u.getId())
                            .username(u.getUsername())
                            .fullName(u.getFullName())
                            .role(m.getRole())
                            .joinedAt(m.getJoinedAt())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(value = {"groupMembers", "userGroups"}, allEntries = true)
    public void addMember(UUID groupId, AddMemberRequest request, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "Group not found"));

        // Check caller role
        GroupMember me = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, currentUserId)
                .orElseThrow(() -> new AppException(AuthError.FORBIDDEN, "You are not a member of this group"));

        if (!isOwnerOrAdmin(me)) {
            throw new AppException(AuthError.FORBIDDEN, "Only owner/admin can add members");
        }

        // Target user
        UUID targetUserId = request.getUserId();
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "User not found"));

        // Nếu đã là member thì bỏ qua / hoặc throw
        var existing = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, targetUserId);
        if (existing.isPresent()) {
            // tuỳ bạn: bỏ qua hoặc báo lỗi
            return;
        }

        String role = normalizeRole(request.getRole()); // "admin" hoặc "member"

        // 1. Thêm vào Keycloak nếu có
        if (group.getKeycloakGroupId() != null) {
            try {
                keycloakGroupService.addUserToKeycloakGroup(group.getKeycloakGroupId(), targetUserId);
            } catch (Exception e) {
                log.warn("Failed to add user to Keycloak group, continuing with database only: {}", e.getMessage());
                // Tiếp tục với database nếu Keycloak fail
            }
        }

        // 2. Thêm vào database
        GroupMember gm = GroupMember.builder()
                .group(group)
                .user(target)
                .role(role)
                .joinedAt(new Timestamp(System.currentTimeMillis()))
                .build();

        groupMemberRepository.save(gm);

        // Evict cache
        cacheService.evictGroupMemberCache(groupId);
        cacheService.evictUserGroupsCache(targetUserId);
        cacheService.evictGroupCache(groupId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"groupMembers", "userGroups"}, allEntries = true)
    public void removeMember(UUID groupId, UUID targetUserId, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "Group not found"));

        GroupMember target = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, targetUserId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "Member not found"));

        boolean selfLeave = currentUserId.equals(targetUserId);

        GroupMember me = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, currentUserId)
                .orElseThrow(() -> new AppException(AuthError.FORBIDDEN, "You are not a member of this group"));

        // Rule:
        // - Member được phép tự rời (selfLeave = true), trừ owner (tuỳ).
        // - Owner/admin được kick member khác, nhưng:
        //   + Không được kick owner
        //   + Admin không được kick admin/owner (tuỳ bạn)

        if (selfLeave) {
            if ("owner".equalsIgnoreCase(target.getRole())) {
                throw new AppException(SystemError.INVALID_PARAM, "Owner cannot leave group directly, transfer ownership first");
            }
        } else {
            // Kick người khác
            if (!isOwnerOrAdmin(me)) {
                throw new AppException(AuthError.FORBIDDEN, "Only owner/admin can remove other members");
            }

            if ("owner".equalsIgnoreCase(target.getRole())) {
                throw new AppException(SystemError.INVALID_PARAM, "Cannot remove group owner");
            }

            if ("admin".equalsIgnoreCase(target.getRole())
                    && "admin".equalsIgnoreCase(me.getRole())) {
                throw new AppException(AuthError.FORBIDDEN, "Admin cannot remove another admin");
            }
        }

        // 1. Xóa khỏi Keycloak nếu có
        if (group.getKeycloakGroupId() != null) {
            try {
                keycloakGroupService.removeUserFromKeycloakGroup(group.getKeycloakGroupId(), targetUserId);
            } catch (Exception e) {
                log.warn("Failed to remove user from Keycloak group, continuing with database only: {}", e.getMessage());
                // Tiếp tục với database nếu Keycloak fail
            }
        }

        // 2. Xóa khỏi database
        groupMemberRepository.delete(target);

        // Evict cache
        cacheService.evictGroupMemberCache(groupId);
        cacheService.evictUserGroupsCache(targetUserId);
        cacheService.evictGroupCache(groupId);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"groupMembers", "userGroups", "groupCache"}, allEntries = true)
    public void deleteGroup(UUID groupId, Authentication auth) {
        UUID currentUserId = SecurityUtils.requireCurrentUserId(auth, userRepository);

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new AppException(SystemError.NOT_FOUND, "Group not found"));

        // Chỉ owner mới được xóa group
        if (group.getOwner() == null || !group.getOwner().getId().equals(currentUserId)) {
            throw new AppException(AuthError.FORBIDDEN, "Only group owner can delete the group");
        }

        // Lấy danh sách members trước khi xóa
        var members = groupMemberRepository.findByGroup_Id(groupId);

        // 1. Xóa tất cả members khỏi Keycloak nếu có
        if (group.getKeycloakGroupId() != null) {
            try {
                for (GroupMember member : members) {
                    try {
                        keycloakGroupService.removeUserFromKeycloakGroup(group.getKeycloakGroupId(), member.getUser().getId());
                    } catch (Exception e) {
                        log.warn("Failed to remove user {} from Keycloak group: {}", member.getUser().getId(), e.getMessage());
                    }
                }
                // Xóa group khỏi Keycloak
                try {
                    keycloakGroupService.deleteKeycloakGroup(group.getKeycloakGroupId());
                } catch (Exception e) {
                    log.warn("Failed to delete Keycloak group: {}", e.getMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to clean up Keycloak group, continuing with database deletion: {}", e.getMessage());
            }
        }

        // 2. Xóa tất cả members khỏi database
        groupMemberRepository.deleteAll(members);

        // 3. Xóa group khỏi database
        groupRepository.delete(group);

        // 4. Evict cache cho tất cả members
        for (GroupMember member : members) {
            cacheService.evictUserGroupsCache(member.getUser().getId());
        }
        cacheService.evictGroupCache(groupId);
        cacheService.evictGroupMemberCache(groupId);
    }

    @Override
    @Transactional
    @Cacheable(value = "groupMembers", key = "#groupId.toString() + ':' + #userId.toString()")
    public InternalMembershipResponse checkMembership(UUID groupId, UUID userId) {
        // internal API, không cần auth (gọi từ Document Service có API key riêng)
        var membershipOpt = groupMemberRepository.findByGroup_IdAndUser_Id(groupId, userId);

        if (membershipOpt.isEmpty()) {
            return InternalMembershipResponse.builder()
                    .member(false)
                    .role(null)
                    .build();
        }

        GroupMember gm = membershipOpt.get();
        return InternalMembershipResponse.builder()
                .member(true)
                .role(gm.getRole())
                .build();
    }

    // ====== helper ======
    private GroupResponse toGroupResponse(Group g, long memberCount) {
        return GroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .visibility(g.getVisibility())
                .ownerId(g.getOwner() != null ? g.getOwner().getId() : null)
                .createdAt(g.getCreatedAt())
                .memberCount(memberCount)
                .build();
    }

    private boolean isOwnerOrAdmin(GroupMember gm) {
        String r = gm.getRole();
        return "owner".equalsIgnoreCase(r) || "admin".equalsIgnoreCase(r);
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "member";
        }
        String r = role.toLowerCase();
        if (!r.equals("admin") && !r.equals("member")) {
            throw new AppException(SystemError.INVALID_PARAM, "Invalid role: " + role);
        }
        return r;
    }
}
