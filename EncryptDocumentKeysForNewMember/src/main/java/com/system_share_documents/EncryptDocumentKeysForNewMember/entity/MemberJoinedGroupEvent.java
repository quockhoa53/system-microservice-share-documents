package com.system_share_documents.EncryptDocumentKeysForNewMember.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event được consume từ Kafka khi member join group
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberJoinedGroupEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @JsonProperty("requestId")
    private String requestId;
    
    @JsonProperty("groupId")
    private String groupId;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("role")
    private String role;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @JsonProperty("addedBy")
    private String addedBy;
}

















