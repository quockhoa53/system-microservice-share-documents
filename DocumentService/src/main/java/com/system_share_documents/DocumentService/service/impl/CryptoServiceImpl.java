package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.service.CryptoService;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class CryptoServiceImpl implements CryptoService {

    @Override
    public SecretKey generateAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new AppException(BusinessError.FAILED_GENERATE_AES);
        }
    }

    @Override
    public String calculateSha256(InputStream in) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return "sha256:" + Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception e) {
            throw new AppException(BusinessError.FAILED_CHECKSUM);
        }
    }
}
