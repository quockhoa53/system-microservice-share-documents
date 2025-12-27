package com.system_share_documents.AppCommonService.rest.grantAccess;

import com.system_share_documents.AppCommonService.dto.request.CheckAccessRequest;
import com.system_share_documents.AppCommonService.dto.request.GrantAccessRequest;

import java.util.HashMap;

public interface GrantAccessRest {
    HashMap<String, Object> checkGrantAccess(CheckAccessRequest request) throws Exception;
    HashMap<String, Object> createGrantAccess(GrantAccessRequest request) throws Exception;
    void upsertGroupDocumentRecipient(String documentId, String groupId, String accessRole) throws Exception;
}
