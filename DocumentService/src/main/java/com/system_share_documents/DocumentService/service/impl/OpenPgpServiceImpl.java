package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.exception.AppException;
import com.system_share_documents.DocumentService.exception.errorcode.BusinessError;
import com.system_share_documents.DocumentService.exception.errorcode.NotExistError;
import com.system_share_documents.DocumentService.exception.errorcode.ValidationError;
import com.system_share_documents.DocumentService.service.OpenPgpService;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.system_share_documents.DocumentService.utils.AlgorithmUtils.getAlgorithmName;

@Service
public class OpenPgpServiceImpl implements OpenPgpService {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, PGPPublicKey> recipientKeyCache = new ConcurrentHashMap<>();

    private PGPPublicKey getEncryptionKey(String recipientId, String armoredKey) throws Exception {
        return recipientKeyCache.computeIfAbsent(recipientId, id -> {
            try (InputStream keyIn = new ByteArrayInputStream(armoredKey.getBytes())) {
                PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(
                        PGPUtil.getDecoderStream(keyIn),
                        new JcaKeyFingerprintCalculator()
                );

                for (PGPPublicKeyRing kRing : pgpPub) {
                    for (PGPPublicKey k : kRing) {
                        if (k.isEncryptionKey()) return k;
                    }
                }
                throw new AppException(NotExistError.ENCRYPTION_KEY_NOT_FOUND);
            } catch (Exception e) {
                throw new AppException(BusinessError.FAILED_GET_ENCRYPTION, e.getMessage());
            }
        });
    }

    @Override
    public byte[] wrapCekWithRecipientPublicKey(String recipientId, byte[] cekBytes, String recipientPublicKeyArmored) {
        try {
            if (cekBytes == null || cekBytes.length == 0)
                throw new AppException(ValidationError.CEK_BYTE_EMPTY);
            if (cekBytes.length != 32) {
                throw new AppException(ValidationError.CEK_BYTE_EMPTY,
                        String.format("CEK length is %d bytes, expected 32 bytes for AES-256", cekBytes.length));
            }
            if (recipientPublicKeyArmored == null || recipientPublicKeyArmored.isBlank())
                throw new AppException(ValidationError.RECIPIENT_PUBLIC_KEY_EMPTY);

            PGPPublicKey encKey = getEncryptionKey(recipientId, recipientPublicKeyArmored);

            JcePGPDataEncryptorBuilder dataEncryptor = new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                    .setWithIntegrityPacket(true)
                    .setSecureRandom(RANDOM)
                    .setProvider("BC");

            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(dataEncryptor);
            encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(encKey).setProvider("BC"));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (OutputStream cOut = encGen.open(out, new byte[8192])) {
                PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
                // CRITICAL: Use PGPLiteralData.BINARY and specify exact length (32 bytes)
                // The filename "_CEK" is just metadata, the actual data is 32 bytes
                try (OutputStream pOut = lData.open(cOut, PGPLiteralData.BINARY, "_CEK", 32, new Date())) {
                    // Write exactly 32 bytes
                    pOut.write(cekBytes, 0, 32);
                }
            }

            return out.toByteArray();
        } catch (Exception e) {
            throw new AppException(BusinessError.FAILED_WRAP_CEK, e.getMessage());
        }
    }

    @Override
    public boolean verifyDetachedSignature(byte[] data, byte[] detachedSignature, String publicKeyArmored) throws Exception {
        if (data == null || data.length == 0)
            throw new AppException(ValidationError.CEK_BYTE_EMPTY);
        if (detachedSignature == null || detachedSignature.length == 0)
            throw new AppException(ValidationError.SIGNATURE_EMPTY);
        if (publicKeyArmored == null || publicKeyArmored.isBlank())
            throw new AppException(ValidationError.RECIPIENT_PUBLIC_KEY_EMPTY);

        Security.addProvider(new BouncyCastleProvider());

        try (
                InputStream keyIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(publicKeyArmored.getBytes(StandardCharsets.UTF_8)));
                InputStream sigIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(detachedSignature))
        ) {
            PGPPublicKeyRingCollection pgpPubRingCollection = new PGPPublicKeyRingCollection(keyIn, new JcaKeyFingerprintCalculator());
            PGPObjectFactory pgpFact = new PGPObjectFactory(sigIn, new JcaKeyFingerprintCalculator());
            Object obj = pgpFact.nextObject();
            PGPSignatureList sigList = (obj instanceof PGPSignatureList)
                    ? (PGPSignatureList) obj
                    : new PGPSignatureList((PGPSignature) obj);
            PGPSignature sig = sigList.get(0);

            PGPPublicKey key = pgpPubRingCollection.getPublicKey(sig.getKeyID());
            if (key == null)
                throw new AppException(NotExistError.PUBLIC_KEY_SIGNATURE_NOT_FOUND);

            sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), key);
            sig.update(data);
            return sig.verify();
        }
    }

    @Override
    public byte[] signDetached(byte[] data, String privateKeyArmored, char[] passphrase) throws Exception {
        if (data == null || data.length == 0)
            throw new AppException(ValidationError.CEK_BYTE_EMPTY);
        if (privateKeyArmored == null || privateKeyArmored.isBlank())
            throw new IllegalArgumentException("Private key must not be empty");

        try (InputStream keyIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(privateKeyArmored.getBytes(StandardCharsets.UTF_8)))) {
            PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(keyIn, new JcaKeyFingerprintCalculator());

            PGPSecretKey secretKey = null;
            for (PGPSecretKeyRing keyRing : pgpSec) {
                for (PGPSecretKey key : keyRing) {
                    if (key.isSigningKey()) {
                        secretKey = key;
                        break;
                    }
                }
                if (secretKey != null) break;
            }

            if (secretKey == null)
                throw new IllegalArgumentException("No signing key found in private key");

            PGPPrivateKey privateKey = secretKey.extractPrivateKey(
                    new org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder()
                            .setProvider("BC")
                            .build(passphrase)
            );

            PGPSignatureGenerator sigGen = new PGPSignatureGenerator(
                    new org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder(
                            secretKey.getPublicKey().getAlgorithm(),
                            PGPUtil.SHA256
                    ).setProvider("BC")
            );

            sigGen.init(PGPSignature.BINARY_DOCUMENT, privateKey);
            sigGen.update(data);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ArmoredOutputStream armorOut = new ArmoredOutputStream(out)) {
                sigGen.generate().encode(armorOut);
            }

            return out.toByteArray();
        }
    }

    /**
     * Lấy tên algorithm từ signature và public key
     * @param detachedSignature Signature bytes
     * @param publicKeyArmored Public key armored string
     * @return Tên algorithm (vd: "Ed25519", "RSA", "ECDSA")
     */
    public String getAlgorithmFromSignature(byte[] detachedSignature, String publicKeyArmored) {
        try {
            java.security.Security.addProvider(new BouncyCastleProvider());
            try (
                    InputStream keyIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(publicKeyArmored.getBytes(StandardCharsets.UTF_8)));
                    InputStream sigIn = PGPUtil.getDecoderStream(new ByteArrayInputStream(detachedSignature))
            ) {
                PGPPublicKeyRingCollection pgpPubRingCollection = new PGPPublicKeyRingCollection(keyIn, new JcaKeyFingerprintCalculator());
                PGPObjectFactory pgpFact = new PGPObjectFactory(sigIn, new JcaKeyFingerprintCalculator());
                Object obj = pgpFact.nextObject();
                PGPSignatureList sigList = (obj instanceof PGPSignatureList)
                        ? (PGPSignatureList) obj
                        : new PGPSignatureList((PGPSignature) obj);
                PGPSignature sig = sigList.get(0);

                PGPPublicKey key = pgpPubRingCollection.getPublicKey(sig.getKeyID());
                if (key == null) {
                    return "UNKNOWN";
                }

                int algId = key.getAlgorithm();
                return getAlgorithmName(algId);
            }
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

}
