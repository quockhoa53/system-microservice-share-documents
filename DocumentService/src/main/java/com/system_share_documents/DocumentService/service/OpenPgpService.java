package com.system_share_documents.DocumentService.service;

public interface OpenPgpService {
    byte[] wrapCekWithRecipientPublicKey(String recipientId, byte[] cekBytes, String recipientPublicKeyArmored) throws Exception;
    boolean verifyDetachedSignature(byte[] data, byte[] detachedSignature, String publicKeyArmored) throws Exception;
    byte[] signDetached(byte[] data, String privateKeyArmored, char[] passphrase) throws Exception;
}
