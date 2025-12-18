package com.system_share_documents.DocumentService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetGroupDocumentsRequest {
    private String groupId;
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 20;
}
