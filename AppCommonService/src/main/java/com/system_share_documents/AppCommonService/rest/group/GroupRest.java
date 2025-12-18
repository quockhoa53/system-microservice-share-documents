package com.system_share_documents.AppCommonService.rest.group;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GroupRest {
    Map<String, Object> checkMembership(UUID groupId, UUID userId);
    List<Map<String, Object>> getGroupMembers(UUID groupId);
}
