package com.system_share_documents.UserService.service;

import com.system_share_documents.UserService.dto.request.AddMemberRequest;
import com.system_share_documents.UserService.dto.request.ChangeMemberRoleRequest;
import com.system_share_documents.UserService.dto.request.CreateGroupRequest;
import com.system_share_documents.UserService.dto.response.GroupDetailResponse;
import com.system_share_documents.UserService.dto.response.GroupMemberResponse;
import com.system_share_documents.UserService.dto.response.GroupResponse;
import com.system_share_documents.UserService.dto.response.InternalMembershipResponse;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

public interface GroupService {
    GroupResponse createGroup(CreateGroupRequest request, Authentication auth);

    List<GroupResponse> getMyGroups(Authentication auth);

    GroupDetailResponse getGroupDetail(UUID groupId, Authentication auth);

    List<GroupMemberResponse> listMembers(UUID groupId, Authentication auth);

    void addMember(UUID groupId, AddMemberRequest request, Authentication auth);

    void removeMember(UUID groupId, UUID targetUserId, Authentication auth);

    void changeMemberRole(UUID groupId, UUID userId, ChangeMemberRoleRequest request, Authentication auth);

    void deleteGroup(UUID groupId, Authentication auth);

    InternalMembershipResponse checkMembership(UUID groupId, UUID userId);
}
