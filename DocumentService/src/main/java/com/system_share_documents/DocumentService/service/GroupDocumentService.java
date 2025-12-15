package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.AddDocumentToGroupRequest;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentResponse;

public interface GroupDocumentService {
    GroupDocumentResponse addDocumentToGroup(AddDocumentToGroupRequest request, String userId);
}
