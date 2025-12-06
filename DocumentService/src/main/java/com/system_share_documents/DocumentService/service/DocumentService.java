package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import org.springframework.data.domain.Page;

public interface DocumentService {
    Page<DocumentResponse> getListDocumentOfUser(String userId, int page, int size) throws Exception;
}
