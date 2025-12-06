package com.system_share_documents.AppCommonService.service;

public interface VaultTransitService {
    String encrypt(String plaintextBase64);
    String decrypt(String ciphertext);
}
