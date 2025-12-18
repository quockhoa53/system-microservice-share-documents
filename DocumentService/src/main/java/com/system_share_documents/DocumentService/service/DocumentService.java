package com.system_share_documents.DocumentService.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.system_share_documents.DocumentService.dto.request.DeleteDocumentRequest;
import com.system_share_documents.DocumentService.dto.request.RequestAccessRequest;
import com.system_share_documents.DocumentService.dto.response.DeleteDocumentResponse;
import com.system_share_documents.DocumentService.dto.response.DocumentResponse;
import com.system_share_documents.DocumentService.dto.response.RequestAccessResponse;
import com.system_share_documents.DocumentService.dto.response.SharedDocumentResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;

import java.util.List;

public interface DocumentService {
    Page<DocumentResponse> getListDocumentOfUser(String userId, int page, int size) throws Exception;
    List<DocumentResponse> searchDocuments(String query, Integer limit, String userIdForFilter, String userIdForPermissions);
    Page<SharedDocumentResponse> getSharedDocuments(String userId, int page, int size) throws JsonProcessingException;
    DeleteDocumentResponse deleteDocument(DeleteDocumentRequest request, String userId, HttpServletRequest httpRequest) throws Exception;
    RequestAccessResponse requestAccess(RequestAccessRequest request, String userId, HttpServletRequest httpRequest) throws Exception;
}
