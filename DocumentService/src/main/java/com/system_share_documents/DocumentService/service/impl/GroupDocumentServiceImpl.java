package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.dto.request.AddDocumentToGroupRequest;
import com.system_share_documents.DocumentService.dto.response.GroupDocumentResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.AuthError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.service.GroupDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GroupDocumentServiceImpl implements GroupDocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired

    @Override
    public GroupDocumentResponse addDocumentToGroup(AddDocumentToGroupRequest request, String userId) {
        String groupId = request.getGroupId();
        if (groupId == null || groupId.isEmpty()) {
            throw new AppException(ValidationError.GROUP_ID_EMPTY);
        }

        Document document = documentRepository.findByIdAndNotDeleted(UUID.fromString(request.getDocumentId()))
                .orElseThrow(() -> new AppException(NotExistError.DOCUMENT_NOT_FOUND));

        if (!document.getOwnerId().equals(userId)) {
            throw new AppException(AuthError.FORBIDDEN_ACTION_ADD);
        }


    }
}
