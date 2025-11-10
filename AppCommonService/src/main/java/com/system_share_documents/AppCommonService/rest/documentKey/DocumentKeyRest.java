package com.system_share_documents.AppCommonService.rest.documentKey;

import com.system_share_documents.AppCommonService.dto.request.CreateDocumentKeyRequest;

public interface DocumentKeyRest {
    void createAndSaveKey(CreateDocumentKeyRequest request) throws Exception;
}
