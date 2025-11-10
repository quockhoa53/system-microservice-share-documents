package com.system_share_documents.AppCommonService.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatermarkProcessEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private String requestId;
    private String documentId;
    private String versionId;
    private String watermarkedKey;
    private String checksum;
    private Timestamp createAt;
}
