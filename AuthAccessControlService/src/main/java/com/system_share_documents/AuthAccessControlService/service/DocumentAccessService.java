package com.system_share_documents.AuthAccessControlService.service;

import com.system_share_documents.AuthAccessControlService.dto.request.CheckAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GetListRecipientsRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.GrantAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.request.RevokeAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.response.DocumentAccessResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

//Thêm check chủ tài liệu mới được cấp và cập nhật quyền
public interface DocumentAccessService {
    List<DocumentAccessResponse> grantAccessDocument(GrantAccessRequest request, HttpServletRequest httpRequest) throws Exception;
    List<DocumentAccessResponse> revokeGrantAccessDocument(RevokeAccessRequest request, HttpServletRequest httpRequest) throws Exception;
    List<DocumentAccessResponse> listRecipientsDocument(GetListRecipientsRequest request, HttpServletRequest httpRequest) throws Exception;
    DocumentAccessResponse checkAccessDocument(CheckAccessRequest request, HttpServletRequest httpRequest) throws Exception;
}
