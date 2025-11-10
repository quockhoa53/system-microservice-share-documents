package com.system_share_documents.AppCommonService.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatermarkJobEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private String requestId;
    private String documentId;
    private String versionId;
    private String ownerId;
    private int versionNumber;
    private String uploadObjectKey;
    private String checksum;
    private String contentType;
    private List<String> recipients;
    @Builder.Default
    private int attempt = 0;
}
