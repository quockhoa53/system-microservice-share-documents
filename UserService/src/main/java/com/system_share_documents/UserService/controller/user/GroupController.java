package com.system_share_documents.UserService.controller.user;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.request.AddMemberRequest;
import com.system_share_documents.UserService.dto.request.CreateGroupRequest;
import com.system_share_documents.UserService.dto.response.GroupDetailResponse;
import com.system_share_documents.UserService.dto.response.GroupMemberResponse;
import com.system_share_documents.UserService.dto.response.GroupResponse;
import com.system_share_documents.UserService.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ApiResponse<GroupResponse> createGroup(
            @RequestBody @Valid CreateGroupRequest request,
            Authentication auth
    ) {
        GroupResponse group = groupService.createGroup(request, auth);
        return ApiResponse.success("OK", "Group created", group);
    }
    @GetMapping("/my")
    public ApiResponse<List<GroupResponse>> myGroups(Authentication auth) {
        var groups = groupService.getMyGroups(auth);
        return ApiResponse.success("OK", "My groups", groups);
    }

    @GetMapping("/{groupId}")
    public ApiResponse<GroupDetailResponse> getGroup(
            @PathVariable UUID groupId,
            Authentication auth
    ) {
        var group = groupService.getGroupDetail(groupId, auth);
        return ApiResponse.success("OK", "Group detail", group);
    }

    @GetMapping("/{groupId}/members")
    public ApiResponse<List<GroupMemberResponse>> listMembers(
            @PathVariable UUID groupId,
            Authentication auth
    ) {
        var members = groupService.listMembers(groupId, auth);
        return ApiResponse.success("OK", "Group members", members);
    }

    @PostMapping("/{groupId}/members")
    public ApiResponse<Void> addMember(
            @PathVariable UUID groupId,
            @RequestBody @Valid AddMemberRequest request,
            Authentication auth
    ) {
        groupService.addMember(groupId, request, auth);
        return ApiResponse.success("OK", "Member added", null);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            Authentication auth
    ) {
        groupService.removeMember(groupId, userId, auth);
        return ApiResponse.success("OK", "Member removed", null);
    }
}
