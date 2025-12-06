package com.system_share_documents.AppCommonService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCacheResponse {
    private String id;
    private long size_bytes;
    private String storage_class;
    private String owner_id;
    private String checksum;
    private String content_type;
    private String original_filename;
    private String metadata;
    private long created_at;
    private long updated_at;
}
