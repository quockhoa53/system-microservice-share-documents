package com.system_share_documents.DocumentService.service;

import com.system_share_documents.DocumentService.dto.request.CompleteUploadRequest;
import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.request.ReinitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.CompleteUploadResponse;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface UploadDocumentService {
    InitUploadResponse initUpload(InitUploadRequest request, String ownerId, HttpServletRequest httpRequest) throws Exception;
    CompleteUploadResponse completeUpload(CompleteUploadRequest request, HttpServletRequest httpRequest) throws Exception;
    InitUploadResponse reinitUpload(ReinitUploadRequest request, String userId, HttpServletRequest httpRequest) throws Exception;
}
