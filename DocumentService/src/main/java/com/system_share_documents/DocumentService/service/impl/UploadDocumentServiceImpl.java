package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.AppCommonService.rest.minio.MinioStorageRest;
import com.system_share_documents.AppCommonService.rest.userkey.UserKeyRest;
import com.system_share_documents.DocumentService.dto.request.InitUploadRequest;
import com.system_share_documents.DocumentService.dto.response.InitUploadResponse;
import com.system_share_documents.DocumentService.dto.response.UploadUrlsResponse;
import com.system_share_documents.DocumentService.entity.Document;
import com.system_share_documents.DocumentService.entity.DocumentKey;
import com.system_share_documents.DocumentService.entity.DocumentVersion;
import com.system_share_documents.DocumentService.enums.VersionStatus;
import com.system_share_documents.DocumentService.repository.DocumentKeyRepository;
import com.system_share_documents.DocumentService.repository.DocumentRepository;
import com.system_share_documents.DocumentService.repository.DocumentVersionRepository;
import com.system_share_documents.DocumentService.service.CryptoService;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import com.system_share_documents.DocumentService.service.UploadDocumentService;
import com.system_share_documents.DocumentService.utils.SerializeUtils;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UploadDocumentServiceImpl implements UploadDocumentService {

    @Autowired
    private CryptoService cryptoService;

    @Autowired
    private OpenPgpService openPgpService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentKeyRepository documentKeyRepository;

    @Autowired
    private UserKeyRest userKeyRepository;

    @Autowired
    MinioStorageRest minioStorageRest;

    @Autowired
    private SerializeUtils serializeUtils;

    private final int presignExpiryMinutes = 15;

    /**
     * Khởi tạo quy trình upload tài liệu:
     * 1. Tạo Document và DocumentVersion (status = UPLOADING).
     * 2. Sinh CEK (AES) và wrap sẵn cho recipients (nếu có public key).
     * 3. Sinh preSigned URL để client PUT file vào staging bucket.
     * 4. Ghi nhận audit log.
     *
     * @param request     Thông tin file upload (tên file, MIME type, recipients…)
     * @param ownerId ID của user thực hiện upload (từ Authentication)
     * @return InitUploadResponse chứa documentId, versionNumber, uploadUrls, checksumRequired
     */

    @Override
    @Transactional
    public InitUploadResponse initUpload(InitUploadRequest request, String ownerId) throws Exception {
        Document doc = Document.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .originalFilename(request.getOriginalFilename())
                .contentType(request.getContentType())
                .sizeBytes(request.getSizeBytes())
                .storageClass(request.getStorageClass() == null ? "standard" : request.getStorageClass())
                .metadata(serializeUtils.serializeMetadata(request.getMetadata()))
                .createdAt(Timestamp.from(Instant.now()))
                .updatedAt(Timestamp.from(Instant.now()))
                .build();
        documentRepository.save(doc);

        DocumentVersion version = DocumentVersion.builder()
                .id(UUID.randomUUID())
                .document(doc)
                .versionNumber(1)
                .status(VersionStatus.UPLOADING)
                .createdAt(Timestamp.from(Instant.now()))
                .build();
        documentVersionRepository.save(version);

        List<String> recipients = request.getRecipients();
        SecretKey cek = cryptoService.generateAesKey();
        byte[] cekBytes = cek.getEncoded();
        if (recipients != null){
            for(String recipient : recipients){
                try {
                    String publicKey = userKeyRepository.getUserPublicKeyForUser(recipient);
                    if(publicKey != null){
                        byte[] wrapped = openPgpService.wrapCekWithRecipientPublicKey(cekBytes, publicKey);
                        DocumentKey documentKey = DocumentKey.builder()
                                .id(UUID.randomUUID())
                                .documentVersion(version)
                                .recipientId(recipient)
                                .wrappedCek(wrapped)
                                .algorithm("OpenPGP-AES256")
                                .createdAt(Timestamp.from(Instant.now()))
                                .build();
                        documentKeyRepository.save(documentKey);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        String objectKey = String.format("staging/%s/v%d/%s", doc.getId(), version.getVersionNumber(), UUID.randomUUID());
        String preSignedUrl = minioStorageRest.generatePreSignedPutUrl(objectKey, presignExpiryMinutes);

        UploadUrlsResponse urls = new UploadUrlsResponse(objectKey, preSignedUrl, null);
        return new InitUploadResponse(doc.getId(), version.getVersionNumber(), urls, true);
    }
}
