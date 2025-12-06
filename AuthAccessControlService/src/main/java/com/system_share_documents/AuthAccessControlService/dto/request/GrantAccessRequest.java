package com.system_share_documents.AuthAccessControlService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrantAccessRequest {
    private String documentId;
    private List<AccessRecipientRequest> recipients;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccessRecipientRequest {
        private String recipientUserId; // ID user nhận quyền
        private String accessRole;      // reader, editor, admin
        private String expirationDays;  // Số ngày cho đến khi hết hạn
        private Boolean canDownload;    // Có cho phép download không
    }
}
