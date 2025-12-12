package com.system_share_documents.DocumentService.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.dto.response.SharedDocumentResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface DocumentService {
    Page<DocumentResponse> getListDocumentOfUser(String userId, int page, int size) throws Exception;
    List<DocumentResponse> searchDocuments(String query, Integer limit);
    Page<SharedDocumentResponse> getSharedDocuments(String userId, int page, int size) throws JsonProcessingException;
}
