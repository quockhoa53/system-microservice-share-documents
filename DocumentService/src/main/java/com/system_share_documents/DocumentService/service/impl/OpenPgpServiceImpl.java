package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.service.OpenPgpService;
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
                throw new IllegalArgumentException("No encryption key found in recipient public key");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Wrap CEK (Content Encryption Key) bằng public key PGP của recipient.
     * 1. Xác thực input CEK và public key.
     * 2. Lấy PGPPublicKey từ cache hoặc parse từ armored key.
     * 3. Khởi tạo PGPEncryptedDataGenerator (AES-256, integrity check, SecureRandom).
     * 4. Ghi CEK vào PGP literal data → CEK được mã hóa.
     * 5. Trả về CEK đã wrap để lưu vào DocumentKey.
     * Lưu ý:
     *   - CEK không lưu plaintext, bảo mật end-to-end.
     *   - Cache public key giúp tăng hiệu năng.
     *
     * @param cekBytes CEK cần wrap
     * @param recipientPublicKeyArmored Public key PGP recipient
     * @return byte[] CEK đã được PGP encrypt
     */

    @Override
    public byte[] wrapCekWithRecipientPublicKey(byte[] cekBytes, String recipientPublicKeyArmored) {
        try {
            if (cekBytes == null || cekBytes.length == 0)
                throw new IllegalArgumentException("CEK bytes must not be null or empty");
            if (recipientPublicKeyArmored == null || recipientPublicKeyArmored.isBlank())
                throw new IllegalArgumentException("Recipient public key must not be null or empty");

            PGPPublicKey encKey = getEncryptionKey(recipientPublicKeyArmored, recipientPublicKeyArmored);

            JcePGPDataEncryptorBuilder dataEncryptor = new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                    .setWithIntegrityPacket(true)
                    .setSecureRandom(RANDOM)
                    .setProvider("BC");

            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(dataEncryptor);
            encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(encKey).setProvider("BC"));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (OutputStream cOut = encGen.open(out, new byte[8192])) {
                PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
                try (OutputStream pOut = lData.open(cOut, PGPLiteralData.BINARY, "_CEK", cekBytes.length, new Date())) {
                    pOut.write(cekBytes);
                }
            }

            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap CEK with recipient public key", e);
        }
    }

    @Override
    public boolean verifyDetachedSignature(byte[] data, byte[] detachedSignature, String publicKeyArmored) throws Exception {
        if (data == null || data.length == 0) throw new IllegalArgumentException("Data must not be empty");
        if (detachedSignature == null || detachedSignature.length == 0) throw new IllegalArgumentException("Signature must not be empty");
        if (publicKeyArmored == null || publicKeyArmored.isBlank()) throw new IllegalArgumentException("Public key must not be empty");

        try (InputStream sigIn = new ByteArrayInputStream(detachedSignature);
             InputStream keyIn = new ByteArrayInputStream(publicKeyArmored.getBytes(StandardCharsets.UTF_8))) {

            PGPPublicKeyRingCollection pgpPubRingCollection = new PGPPublicKeyRingCollection(
                    PGPUtil.getDecoderStream(keyIn),
                    new JcaKeyFingerprintCalculator()
            );

            PGPSignatureList sigList;
            try (InputStream decoder = PGPUtil.getDecoderStream(sigIn)) {
                PGPObjectFactory pgpFact = new PGPObjectFactory(decoder, new JcaKeyFingerprintCalculator());
                Object obj = pgpFact.nextObject();
                if (obj instanceof PGPSignatureList) {
                    sigList = (PGPSignatureList) obj;
                } else if (obj instanceof PGPSignature) {
                    sigList = new PGPSignatureList((PGPSignature) obj);
                } else {
                    throw new IllegalArgumentException("Invalid detached signature format");
                }
            }

            PGPSignature sig = sigList.get(0);
            PGPPublicKey key = pgpPubRingCollection.getPublicKey(sig.getKeyID());

            sig.init(new JcaPGPContentVerifierBuilderProvider().setProvider("BC"), key);
            sig.update(data);

            return sig.verify();
        }
    }

}
