package com.system_share_documents.UserService.service;

import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;
import java.util.UUID;

public interface KeycloakGroupService {
    
    /**
     * Tạo group trong Keycloak
     */
    GroupRepresentation createKeycloakGroup(String name, String description);
    
    /**
     * Lấy group từ Keycloak theo ID
     */
    GroupRepresentation getKeycloakGroup(String groupId);
    
    /**
     * Lấy tất cả groups từ Keycloak
     */
    List<GroupRepresentation> getAllKeycloakGroups();
    
    /**
     * Thêm user vào group trong Keycloak
     */
    void addUserToKeycloakGroup(String groupId, UUID userId);
    
    /**
     * Xóa user khỏi group trong Keycloak
     */
    void removeUserFromKeycloakGroup(String groupId, UUID userId);
    
    /**
     * Lấy danh sách users trong group
     */
    List<UserRepresentation> getGroupMembers(String groupId);
    
    /**
     * Xóa group trong Keycloak
     */
    void deleteKeycloakGroup(String groupId);
    
    /**
     * Đồng bộ group từ Keycloak vào database
     */
    void syncGroupFromKeycloak(String keycloakGroupId);
    
    /**
     * Đồng bộ tất cả groups từ Keycloak
     */
    void syncAllGroupsFromKeycloak();
}











