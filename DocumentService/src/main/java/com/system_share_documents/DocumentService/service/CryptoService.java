package com.system_share_documents.DocumentService.service;

import javax.crypto.SecretKey;
import java.io.InputStream;

public interface CryptoService {
    SecretKey generateAesKey();
    String calculateSha256(InputStream in);
}
