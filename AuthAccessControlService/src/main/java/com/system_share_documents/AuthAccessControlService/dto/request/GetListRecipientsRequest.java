package com.system_share_documents.AuthAccessControlService.dto.request;

import com.system_share_documents.AppCommonService.validation.ValidEnum;
import com.system_share_documents.AuthAccessControlService.enums.DocumentAccessRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetListRecipientsRequest {
    private String documentId;

    @ValidEnum(enumClass = DocumentAccessRole.class, message = "Type must be a valid DocumentAccessRole")
    private String type;
}
