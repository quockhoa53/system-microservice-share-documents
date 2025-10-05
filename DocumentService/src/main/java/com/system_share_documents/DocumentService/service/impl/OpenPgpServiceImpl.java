package com.system_share_documents.DocumentService.service.impl;

import com.system_share_documents.DocumentService.service.OpenPgpService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;
import java.util.Date;
import java.util.Iterator;

@Service
public class OpenPgpServiceImpl implements OpenPgpService {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Phương thức này thực hiện việc "wrap" (mã hóa) Content Encryption Key (CEK) bằng public key của người nhận,
     * sử dụng chuẩn OpenPGP (BouncyCastle).
     *
     * Cơ chế:
     * - Đọc public key của người nhận từ chuỗi ASCII-armored (hoặc binary).
     * - Lấy ra key có thể dùng cho encryption (PGPPublicKey).
     * - Sử dụng PGPEncryptedDataGenerator để tạo gói dữ liệu PGP.
     * - Ghi CEK (dạng literal data) vào trong gói dữ liệu đã mã hóa bằng public key của người nhận.
     *
     * INPUT:
     *   @param cekBytes CEK (Content Encryption Key) ở dạng byte[]
     *   @param recipientPublicKeyArmored Public key của người nhận (chuỗi ASCII-armored OpenPGP hoặc binary)
     *
     * OUTPUT:
     *   @return byte[] CEK đã được mã hóa bằng public key của người nhận (dùng để gửi kèm tài liệu)
     *
     * Ý nghĩa:
     * - Đảm bảo chỉ người nhận (có private key tương ứng) mới có thể giải mã CEK.
     * - CEK sau khi giải mã sẽ dùng để giải mã nội dung tài liệu.
     */
    @Override
    public byte[] wrapCekWithRecipientPublicKey(byte[] cekBytes, String recipientPublicKeyArmored) {
        try {

            if (cekBytes == null || cekBytes.length == 0) {
                throw new IllegalArgumentException("CEK bytes must not be null or empty");
            }
            if (recipientPublicKeyArmored == null || recipientPublicKeyArmored.isBlank()) {
                throw new IllegalArgumentException("Recipient public key must not be null or empty");
            }

            InputStream keyIn = new ByteArrayInputStream(recipientPublicKeyArmored.getBytes());
            InputStream decoderStream = PGPUtil.getDecoderStream(keyIn);
            PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(decoderStream, new JcaKeyFingerprintCalculator());

            PGPPublicKey encKey = null;
            Iterator<PGPPublicKeyRing> rIter = pgpPub.getKeyRings();
            while (rIter.hasNext() && encKey == null) {
                PGPPublicKeyRing kRing = rIter.next();
                Iterator<PGPPublicKey> kIter = kRing.getPublicKeys();
                while (kIter.hasNext()) {
                    PGPPublicKey k = kIter.next();
                    if (k.isEncryptionKey()) {
                        encKey = k;
                        break;
                    }
                }
            }
            if (encKey == null) {
                throw new IllegalArgumentException("No encryption key found in recipient public key");
            }

            JcePGPDataEncryptorBuilder dataEncryptor = new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                    .setWithIntegrityPacket(true)
                    .setSecureRandom(new java.security.SecureRandom())
                    .setProvider("BC");

            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(dataEncryptor);
            JcePublicKeyKeyEncryptionMethodGenerator methodGen = new JcePublicKeyKeyEncryptionMethodGenerator(encKey).setProvider("BC");
            encGen.addMethod(methodGen);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OutputStream cOut = encGen.open(out, new byte[1 << 16]);
            PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
            try (OutputStream pOut = lData.open(cOut, PGPLiteralData.BINARY, "_CEK", cekBytes.length, new Date())) {
                pOut.write(cekBytes);
            }
            cOut.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to wrap CEK with recipient public key", e);
        }
    }
}
