package com.system_share_documents.UserService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalMembershipResponse {
    private boolean member;  // true nếu user nằm trong group
    private String role;     // "owner" | "admin" | "member" | null
}
