package com.system_share_documents.AuthAccessControlService.service;

import com.system_share_documents.AuthAccessControlService.dto.request.CheckAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GetListRecipientsRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GrantAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.RevokeAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.response.DocumentAccessResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface DocumentAccessService {
    List<DocumentAccessResponse> grantAccessDocument(GrantAccessRequest request, HttpServletRequest httpRequest) throws Exception;
    List<DocumentAccessResponse> revokeGrantAccessDocument(RevokeAccessRequest request, HttpServletRequest httpRequest) throws Exception;
    List<DocumentAccessResponse> listRecipientsDocument(GetListRecipientsRequest request, HttpServletRequest httpRequest) throws Exception;
    DocumentAccessResponse checkAccessDocument(CheckAccessRequest request, HttpServletRequest httpRequest) throws Exception;
    /**
     * Internal API: Tạo hoặc cập nhật DocumentRecipient cho group document
     * Được gọi từ DocumentService khi thêm/cập nhật document vào group
     */
    void upsertGroupDocumentRecipient(String documentId, String groupId, String accessRole) throws Exception;
}
