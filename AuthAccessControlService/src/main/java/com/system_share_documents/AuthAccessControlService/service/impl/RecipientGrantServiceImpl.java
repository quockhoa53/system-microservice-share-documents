package com.system_share_documents.AuthAccessControlService.service.impl;

import com.system_share_documents.AuthAccessControlService.dto.request.GrantAccessRequest;
import com.system_share_documents.AuthAccessControlService.dto.response.DocumentAccessResponse;
import com.system_share_documents.AuthAccessControlService.entity.DocumentRecipient;
import com.system_share_documents.AuthAccessControlService.enums.DocumentAccessRole;
import com.system_share_documents.AuthAccessControlService.enums.RecipientType;
import com.system_share_documents.AuthAccessControlService.repository.DocumentRecipientRepository;
import com.system_share_documents.AuthAccessControlService.service.RecipientGrantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Slf4j
public class RecipientGrantServiceImpl implements RecipientGrantService {

    @Autowired
    private DocumentRecipientRepository documentRecipientRepository;

    @Transactional
    public DocumentAccessResponse processGrantAccessForRecipient(String documentId, GrantAccessRequest.AccessRecipientRequest accessRecipient) {
        try {
            // Tìm DocumentRecipient với recipientType = USER để đảm bảo đúng rule mới
            DocumentRecipient recipient = documentRecipientRepository
                    .findByDocumentIdAndRecipientUserIdAndRecipientType(
                            documentId,
                            accessRecipient.getRecipientUserId(),
                            RecipientType.USER)
                    .orElse(null);

            Timestamp expiresAt = null;
            if (accessRecipient.getExpirationDays() != null && !accessRecipient.getExpirationDays().isEmpty()) {
                try {
                    long expirationDays = Long.parseLong(accessRecipient.getExpirationDays());
                    expiresAt = Timestamp.valueOf(LocalDate.now(ZoneId.systemDefault())
                            .plusDays(expirationDays)
                            .atStartOfDay());
                } catch (NumberFormatException e) {
                    log.warn("Invalid expirationDays format: {}", accessRecipient.getExpirationDays());
                }
            }

            boolean isCanDownload = false;
            DocumentAccessRole role = DocumentAccessRole.valueOf(accessRecipient.getAccessRole());
            if (role == DocumentAccessRole.OWNER || role == DocumentAccessRole.EDITOR || role == DocumentAccessRole.DOWNLOADER) {
                isCanDownload = true;
            }

            boolean isNew = false;
            if (recipient == null) {
                recipient = DocumentRecipient.builder()
                        .documentId(documentId)
                        .recipientType(RecipientType.USER) // Set recipientType = USER cho direct access
                        .recipientUserId(accessRecipient.getRecipientUserId())
                        .recipientGroupId(null) // null cho USER type
                        .accessRole(DocumentAccessRole.valueOf(accessRecipient.getAccessRole() != null ? accessRecipient.getAccessRole() : DocumentAccessRole.VIEWER.name()))
                        .canDownload(isCanDownload)
                        .expiresAt(expiresAt)
                        .isRevoke(false)
                        .createdAt(Timestamp.from(Instant.now()))
                        .updatedAt(Timestamp.from(Instant.now()))
                        .build();
                isNew = true;
            } else {
                // Cập nhật recipient hiện có
                if (accessRecipient.getAccessRole() != null) {
                    recipient.setAccessRole(DocumentAccessRole.valueOf(accessRecipient.getAccessRole()));
                }
                if (expiresAt != null) {
                    recipient.setExpiresAt(expiresAt);
                }
                recipient.setCanDownload(isCanDownload);
                recipient.setIsRevoke(false); // Reset revoke flag khi cập nhật quyền
                recipient.setUpdatedAt(Timestamp.from(Instant.now()));
            }

            documentRecipientRepository.save(recipient);

            return DocumentAccessResponse.builder()
                    .documentId(documentId)
                    .recipientUserId(accessRecipient.getRecipientUserId())
                    .accessRole(String.valueOf(recipient.getAccessRole()))
                    .canDownload(recipient.getCanDownload())
                    .expiresAt(recipient.getExpiresAt())
                    .grantedAt(recipient.getCreatedAt())
                    .message(isNew ? "Cấp quyền thành công" : "Cập nhật quyền thành công")
                    .build();

        } catch (Exception e) {
            log.error("Error granting/updating access for user {}: {}", accessRecipient.getRecipientUserId(), e.getMessage());
            return DocumentAccessResponse.builder()
                    .documentId(documentId)
                    .recipientUserId(accessRecipient.getRecipientUserId())
                    .message("Lỗi khi cấp quyền: " + e.getMessage())
                    .build();
        }
    }
}
