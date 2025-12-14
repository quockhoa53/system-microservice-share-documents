package com.system_share_documents.DocumentService.utils;

import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;

public class AlgorithmUtils {

    /**
     * Chuyển đổi algorithm ID sang tên algorithm
     * @param algorithmId Algorithm ID từ BouncyCastle
     * @return Tên algorithm
     */
    public static String getAlgorithmName(int algorithmId) {
        switch (algorithmId) {
            case PublicKeyAlgorithmTags.RSA_GENERAL:
            case PublicKeyAlgorithmTags.RSA_SIGN:
            case PublicKeyAlgorithmTags.RSA_ENCRYPT:
                return "RSA";
            case PublicKeyAlgorithmTags.DSA:
                return "DSA";
            case PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT:
            case PublicKeyAlgorithmTags.ELGAMAL_GENERAL:
                return "ElGamal";
            case PublicKeyAlgorithmTags.ECDSA:
                return "ECDSA";
            case PublicKeyAlgorithmTags.ECDH:
                return "ECDH";
            case PublicKeyAlgorithmTags.EDDSA:
                return "Ed25519";
            default:
                return "UNKNOWN";
        }
    }
}
