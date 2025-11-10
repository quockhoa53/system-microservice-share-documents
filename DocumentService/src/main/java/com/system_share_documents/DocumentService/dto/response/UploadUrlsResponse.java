package com.system_share_documents.DocumentService.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadUrlsResponse {
    private String objectKey;
    private String preSignedPutUrl;
    private String uploadId;
}
