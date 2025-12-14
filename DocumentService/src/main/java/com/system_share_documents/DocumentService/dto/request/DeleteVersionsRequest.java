package com.system_share_documents.DocumentService.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteVersionsRequest {
    private UUID documentId;
    private List<UUID> versionIds;
}
