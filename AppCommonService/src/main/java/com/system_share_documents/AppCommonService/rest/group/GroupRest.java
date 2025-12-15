package com.system_share_documents.AppCommonService.rest.group;

import java.util.HashMap;

public interface GroupRest {
    HashMap<String, Object> checkMembership(String groupId, String userId);
}
