package com.system_share_documents.DocumentService.dto.response;

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
public class DeleteVersionsResponse {
    private UUID documentId;
    private List<UUID> deletedVersionIds;
    private int deletedCount;
    private int failedCount;
}
