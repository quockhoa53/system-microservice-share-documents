package com.system_share_documents.AppCommonService.service;

import javax.crypto.SecretKey;
import java.io.InputStream;

public interface CryptoService {
    SecretKey generateAesKey();
    String calculateSha256(InputStream in);
    byte[] encryptFile(byte[] inputBytes, byte[] cek) throws Exception;
    byte[] decryptFile(byte[] encryptedBytes, byte[] cek) throws Exception;
}
