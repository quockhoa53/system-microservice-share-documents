package com.system_share_documents.AuthAccessControlService.service;

import com.system_share_documents.AuthAccessControlService.dto.request.GrantAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.response.DocumentAccessResponse;

public interface RecipientGrantService {
    DocumentAccessResponse processGrantAccessForRecipient(String documentId, GrantAccessRequest.AccessRecipientRequest accessRecipient);
}
