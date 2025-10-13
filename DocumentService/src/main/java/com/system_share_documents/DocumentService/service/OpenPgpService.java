package com.system_share_documents.DocumentService.service;

public interface OpenPgpService {
    byte[] wrapCekWithRecipientPublicKey(byte[] cekBytes, String recipientPublicKeyArmored) throws Exception;
    boolean verifyDetachedSignature(byte[] data, byte[] detachedSignature, String publicKeyArmored) throws Exception;
}
