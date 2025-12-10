package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.UserService.entity.Group;
import com.system_share_documents.UserService.entity.GroupMember;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.repository.GroupMemberRepository;
import com.system_share_documents.UserService.repository.GroupRepository;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.KeycloakGroupService;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakGroupServiceImpl implements KeycloakGroupService {

    private final Keycloak keycloakAdmin;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    /**
     * Realm để quản lý groups (system-share-docs - realm của ứng dụng)
     * Khác với admin-realm (master) dùng để login admin
     */
    @Value("${app.security.keycloak.realm:system-share-docs}")
    private String realm;

    private RealmResource getRealm() {
        return keycloakAdmin.realm(realm);
    }

    @Override
    public GroupRepresentation createKeycloakGroup(String name, String description) {
        try {
            GroupsResource groupsResource = getRealm().groups();
            GroupRepresentation group = new GroupRepresentation();
            group.setName(name);
            if (description != null && !description.isBlank()) {
                group.setAttributes(Map.of("description", List.of(description)));
            }

            Response response = groupsResource.add(group);
            
            if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                String location = response.getLocation().getPath();
                String groupId = location.substring(location.lastIndexOf('/') + 1);
                log.info("Created Keycloak group: {} with ID: {}", name, groupId);
                return groupsResource.group(groupId).toRepresentation();
            } else {
                log.error("Failed to create group in Keycloak. Status: {}", response.getStatus());
                throw new RuntimeException("Failed to create group in Keycloak: " + response.getStatus());
            }
        } catch (Exception e) {
            log.error("Error creating Keycloak group: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Keycloak group: " + e.getMessage(), e);
        }
    }

    @Override
    public GroupRepresentation getKeycloakGroup(String groupId) {
        try {
            return getRealm().groups().group(groupId).toRepresentation();
        } catch (Exception e) {
            log.error("Error getting Keycloak group {}: {}", groupId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<GroupRepresentation> getAllKeycloakGroups() {
        try {
            return getRealm().groups().groups();
        } catch (Exception e) {
            log.error("Error getting all Keycloak groups: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void addUserToKeycloakGroup(String groupId, UUID userId) {
        try {
            GroupResource groupResource = getRealm().groups().group(groupId);
            // Keycloak Admin API: thêm user vào group
            getRealm().users().get(userId.toString()).joinGroup(groupId);
            log.info("User {} added to Keycloak group {}", userId, groupId);
        } catch (Exception e) {
            log.error("Error adding user {} to Keycloak group {}: {}", userId, groupId, e.getMessage(), e);
            throw new RuntimeException("Failed to add user to Keycloak group: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeUserFromKeycloakGroup(String groupId, UUID userId) {
        try {
            // Keycloak Admin API: xóa user khỏi group
            getRealm().users().get(userId.toString()).leaveGroup(groupId);
            log.info("User {} removed from Keycloak group {}", userId, groupId);
        } catch (Exception e) {
            log.error("Error removing user {} from Keycloak group {}: {}", userId, groupId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove user from Keycloak group: " + e.getMessage(), e);
        }
    }

    @Override
    public List<UserRepresentation> getGroupMembers(String groupId) {
        try {
            return getRealm().groups().group(groupId).members();
        } catch (Exception e) {
            log.error("Error getting members of Keycloak group {}: {}", groupId, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void deleteKeycloakGroup(String groupId) {
        try {
            getRealm().groups().group(groupId).remove();
            log.info("Keycloak group {} deleted", groupId);
        } catch (Exception e) {
            log.error("Error deleting Keycloak group {}: {}", groupId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete Keycloak group: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void syncGroupFromKeycloak(String keycloakGroupId) {
        try {
            GroupRepresentation kcGroup = getKeycloakGroup(keycloakGroupId);
            if (kcGroup == null) {
                log.warn("Keycloak group {} not found", keycloakGroupId);
                return;
            }

            // Tìm group trong database theo keycloakGroupId
            Optional<Group> existingGroup = groupRepository.findByKeycloakGroupId(keycloakGroupId);
            
            Group group;
            if (existingGroup.isPresent()) {
                group = existingGroup.get();
                // Update thông tin
                group.setName(kcGroup.getName());
                // Lấy description từ attributes
                if (kcGroup.getAttributes() != null && kcGroup.getAttributes().containsKey("description")) {
                    List<String> descList = kcGroup.getAttributes().get("description");
                    if (descList != null && !descList.isEmpty()) {
                        group.setDescription(descList.get(0));
                    }
                }
            } else {
                // Tạo mới group trong database
                // Lấy owner từ Keycloak (member đầu tiên hoặc từ attributes)
                UUID ownerId = extractOwnerId(kcGroup);
                User owner = userRepository.findById(ownerId)
                        .orElseThrow(() -> new RuntimeException("Owner not found: " + ownerId));

                String description = null;
                if (kcGroup.getAttributes() != null && kcGroup.getAttributes().containsKey("description")) {
                    List<String> descList = kcGroup.getAttributes().get("description");
                    if (descList != null && !descList.isEmpty()) {
                        description = descList.get(0);
                    }
                }

                group = Group.builder()
                        .name(kcGroup.getName())
                        .keycloakGroupId(keycloakGroupId)
                        .description(description)
                        .owner(owner)
                        .visibility((short) 0) // Default private
                        .createdAt(Timestamp.from(Instant.now()))
                        .build();
            }

            group = groupRepository.save(group);

            // Đồng bộ members
            List<UserRepresentation> kcMembers = getGroupMembers(keycloakGroupId);
            syncGroupMembers(group, kcMembers);

            log.info("Synced Keycloak group {} to database", keycloakGroupId);
        } catch (Exception e) {
            log.error("Error syncing group from Keycloak {}: {}", keycloakGroupId, e.getMessage(), e);
            throw new RuntimeException("Failed to sync group from Keycloak: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void syncAllGroupsFromKeycloak() {
        try {
            List<GroupRepresentation> kcGroups = getAllKeycloakGroups();
            int synced = 0;
            int failed = 0;
            
            for (GroupRepresentation kcGroup : kcGroups) {
                try {
                    syncGroupFromKeycloak(kcGroup.getId());
                    synced++;
                } catch (Exception e) {
                    log.error("Failed to sync group {}: {}", kcGroup.getId(), e.getMessage());
                    failed++;
                }
            }
            
            log.info("Synced {} groups from Keycloak ({} successful, {} failed)", 
                    kcGroups.size(), synced, failed);
        } catch (Exception e) {
            log.error("Error during sync all groups from Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to sync all groups from Keycloak", e);
        }
    }

    private void syncGroupMembers(Group group, List<UserRepresentation> kcMembers) {
        // Xóa members không còn trong Keycloak
        List<GroupMember> dbMembers = groupMemberRepository.findByGroup_Id(group.getId());
        for (GroupMember dbMember : dbMembers) {
            boolean stillInKeycloak = kcMembers.stream()
                    .anyMatch(m -> m.getId().equals(dbMember.getUser().getId().toString()));
            if (!stillInKeycloak) {
                groupMemberRepository.delete(dbMember);
                log.debug("Removed member {} from group {} (not in Keycloak)", 
                        dbMember.getUser().getId(), group.getId());
            }
        }

        // Thêm members mới từ Keycloak
        for (UserRepresentation kcMember : kcMembers) {
            UUID userId;
            try {
                userId = UUID.fromString(kcMember.getId());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid user ID format in Keycloak: {}", kcMember.getId());
                continue;
            }
            
            Optional<User> user = userRepository.findById(userId);
            if (user.isEmpty()) {
                log.warn("User {} from Keycloak not found in database", userId);
                continue;
            }

            Optional<GroupMember> existing = groupMemberRepository
                    .findByGroup_IdAndUser_Id(group.getId(), userId);
            
            if (existing.isEmpty()) {
                GroupMember gm = GroupMember.builder()
                        .group(group)
                        .user(user.get())
                        .role("member") // Default role
                        .joinedAt(Timestamp.from(Instant.now()))
                        .build();
                groupMemberRepository.save(gm);
                log.debug("Added member {} to group {} from Keycloak", userId, group.getId());
            }
        }
    }

    private UUID extractOwnerId(GroupRepresentation kcGroup) {
        // Lấy owner từ member đầu tiên hoặc từ database nếu đã có
        List<UserRepresentation> members = getGroupMembers(kcGroup.getId());
        if (!members.isEmpty()) {
            try {
                return UUID.fromString(members.get(0).getId());
            } catch (IllegalArgumentException e) {
                log.error("Invalid owner ID format: {}", members.get(0).getId());
            }
        }
        
        // Fallback: tìm trong database nếu group đã tồn tại
        Optional<Group> existing = groupRepository.findByKeycloakGroupId(kcGroup.getId());
        if (existing.isPresent() && existing.get().getOwner() != null) {
            return existing.get().getOwner().getId();
        }
        
        throw new RuntimeException("Cannot determine owner for group: " + kcGroup.getId());
    }
}
