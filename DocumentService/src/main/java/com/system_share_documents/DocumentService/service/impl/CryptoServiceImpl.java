package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.service.CryptoService;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoServiceImpl implements CryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

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

    @Override
    public byte[] encryptFile(byte[] inputBytes, byte[] cek) throws Exception {
        // Tạo IV ngẫu nhiên
        byte[] iv = new byte[IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        // Chuẩn bị cipher AES-GCM
        SecretKeySpec keySpec = new SecretKeySpec(cek, AES_ALGORITHM);
        Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

        // Mã hóa
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        byte[] encrypted = cipher.doFinal(inputBytes);

        // Ghép IV + ciphertext lại (để tiện giải mã)
        byte[] result = new byte[IV_LENGTH + encrypted.length];
        System.arraycopy(iv, 0, result, 0, IV_LENGTH);
        System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);

        return result;
    }

    @Override
    public byte[] decryptFile(byte[] encryptedBytes, byte[] cek) throws Exception {
        // Tách IV và ciphertext
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(encryptedBytes, 0, iv, 0, IV_LENGTH);

        byte[] ciphertext = new byte[encryptedBytes.length - IV_LENGTH];
        System.arraycopy(encryptedBytes, IV_LENGTH, ciphertext, 0, ciphertext.length);

        // Giải mã
        SecretKeySpec keySpec = new SecretKeySpec(cek, AES_ALGORITHM);
        Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        return cipher.doFinal(ciphertext);
    }
}
