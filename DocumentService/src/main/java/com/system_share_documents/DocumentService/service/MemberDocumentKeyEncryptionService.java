package com.system_share_documents.DocumentService.service;

import com.system_share_documents.AppCommonService.event.MemberJoinedGroupEvent;

public interface MemberDocumentKeyEncryptionService {
    /**
     * Mã hóa CEK cho member mới join group với tất cả documents trong group
     */
    void encryptCEKForNewMember(MemberJoinedGroupEvent event) throws Exception;
}

