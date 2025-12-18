package com.system_share_documents.UserService.controller.user;

import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.response.InternalMembershipResponse;
import com.system_share_documents.UserService.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/groups")
@RequiredArgsConstructor
public class GroupInternalController {

    private final GroupService groupService;

    // GET /internal/groups/{groupId}/membership?userId=...
    @GetMapping("/{groupId}/membership")
    public ApiResponse<InternalMembershipResponse> checkMembership(
            @PathVariable UUID groupId,
            @RequestParam UUID userId
    ) {
        InternalMembershipResponse response = groupService.checkMembership(groupId, userId);
        return ApiResponse.success("OK", "Check membership successfully", response);
    }
}
