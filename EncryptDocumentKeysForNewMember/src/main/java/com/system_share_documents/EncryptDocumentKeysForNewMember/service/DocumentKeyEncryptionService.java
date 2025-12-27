package com.system_share_documents.EncryptDocumentKeysForNewMember.service;

import com.system_share_documents.EncryptDocumentKeysForNewMember.entity.MemberJoinedGroupEvent;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPUtil;

import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator;

/**
 * Service để mã hóa CEK cho member mới join group
 */
public class DocumentKeyEncryptionService {

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;
    private final String userServiceUrl;
    private final String vaultUrl;
    private final String vaultToken;
    private final String vaultTransitKeyName;

    public DocumentKeyEncryptionService(Properties props) {
        this.dbUrl = props.getProperty("db.url");
        this.dbUser = props.getProperty("db.user");
        this.dbPassword = props.getProperty("db.password");
        this.userServiceUrl = props.getProperty("user.service.url");
        this.vaultUrl = props.getProperty("vault.url");
        this.vaultToken = props.getProperty("vault.token");
        this.vaultTransitKeyName = props.getProperty("vault.transit.key.name", "document-cek-key");

        // Load PostgreSQL JDBC driver explicitly
        loadPostgreSQLDriver();
    }

    /**
     * Load PostgreSQL JDBC driver explicitly
     */
    private void loadPostgreSQLDriver() {
        try {
            // Try to load driver using Class.forName
            Class.forName("org.postgresql.Driver");
            System.out.println("✅ PostgreSQL JDBC driver loaded successfully via Class.forName");
        } catch (ClassNotFoundException e) {
            // If Class.forName fails, try using ServiceLoader
            try {
                java.util.ServiceLoader<java.sql.Driver> drivers = java.util.ServiceLoader.load(java.sql.Driver.class);
                boolean driverFound = false;
                for (java.sql.Driver driver : drivers) {
                    if (driver.getClass().getName().equals("org.postgresql.Driver")) {
                        driverFound = true;
                        System.out.println("✅ PostgreSQL JDBC driver loaded successfully via ServiceLoader");
                        break;
                    }
                }
                if (!driverFound) {
                    System.err.println("⚠️ PostgreSQL JDBC driver not found via ServiceLoader");
                }
            } catch (Exception ex) {
                System.err.println("❌ Failed to load PostgreSQL JDBC driver: " + ex.getMessage());
                throw new RuntimeException("PostgreSQL JDBC driver not found. Please ensure postgresql dependency is included in the JAR.", ex);
            }
        }
    }

    /**
     * Mã hóa CEK cho member mới với tất cả documents trong group
     */
    public void encryptCEKForNewMember(MemberJoinedGroupEvent event) throws Exception {
        String groupId = event.getGroupId();
        String userId = event.getUserId();

        System.out.println("🔐 Starting encryption for member " + userId + " in group " + groupId);

        // 1. Lấy danh sách documents trong group từ database
        List<UUID> documentIds = getDocumentsInGroup(groupId);
        System.out.println("📄 Found " + documentIds.size() + " documents in group " + groupId);

        if (documentIds.isEmpty()) {
            System.out.println("✅ No documents to encrypt for group " + groupId);
            return;
        }

        // 2. Lấy public key của user mới
        String userPublicKey = getUserPublicKey(userId);
        if (userPublicKey == null || userPublicKey.isBlank()) {
            throw new RuntimeException("User public key not found for user: " + userId);
        }
        System.out.println("🔑 Retrieved public key for user " + userId);

        // 3. Với mỗi document, mã hóa CEK cho tất cả versions
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;

        for (UUID documentId : documentIds) {
            try {
                List<VersionCEK> versions = getDocumentVersions(documentId);
                System.out.println("📦 Document " + documentId + " has " + versions.size() + " versions");

                for (VersionCEK version : versions) {
                    try {
                        if (documentKeyExists(version.versionId, userId)) {
                            System.out.println("⏭️  DocumentKey already exists for version " + version.versionId + ", user " + userId);
                            skipCount++;
                            continue;
                        }

                        // Mã hóa CEK cho member mới
                        encryptCEKForVersion(version, userId, userPublicKey);
                        successCount++;
                        System.out.println("✅ Encrypted CEK for version " + version.versionId + ", user " + userId);
                    } catch (Exception e) {
                        System.err.println("❌ Failed to encrypt CEK for version " + version.versionId + ", user " + userId + ": " + e.getMessage());
                        errorCount++;
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to process document " + documentId + ": " + e.getMessage());
                errorCount++;
            }
        }

        System.out.println("✅ Encryption completed - Success: " + successCount + ", Skipped: " + skipCount + ", Errors: " + errorCount);
    }

    /**
     * Lấy danh sách document IDs trong group
     */
    private List<UUID> getDocumentsInGroup(String groupId) throws SQLException {
        List<UUID> documentIds = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            String sql = "SELECT DISTINCT document_id FROM group_documents WHERE group_id = ? AND deleted_at IS NULL";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, groupId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID docId = (UUID) rs.getObject("document_id");
                        documentIds.add(docId);
                    }
                }
            }
        }

        return documentIds;
    }

    /**
     * Lấy tất cả versions của document với wrappedCEKMaster
     */
    private List<VersionCEK> getDocumentVersions(UUID documentId) throws SQLException {
        List<VersionCEK> versions = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            String sql = "SELECT id, wrapped_cek_master FROM document_versions WHERE document_id = ? AND status = 'AVAILABLE'";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, documentId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID versionId = (UUID) rs.getObject("id");
                        String wrappedCEKMaster = rs.getString("wrapped_cek_master");

                        if (wrappedCEKMaster != null && wrappedCEKMaster.startsWith("vault:")) {
                            versions.add(new VersionCEK(versionId, wrappedCEKMaster));
                        }
                    }
                }
            }
        }

        return versions;
    }

    /**
     * Kiểm tra DocumentKey đã tồn tại chưa
     */
    private boolean documentKeyExists(UUID versionId, String userId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            String sql = "SELECT COUNT(*) FROM document_keys WHERE document_version_id = ? AND recipient_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, versionId);
                stmt.setString(2, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Mã hóa CEK cho một version
     */
    private void encryptCEKForVersion(VersionCEK version, String userId, String userPublicKey) throws Exception {
        // 1. Decrypt CEK master từ Vault
        String rawCEKBase64 = decryptFromVault(version.wrappedCEKMaster);
        byte[] rawCEK = Base64.getDecoder().decode(rawCEKBase64);

        if (rawCEK.length != 32) {
            throw new RuntimeException("Invalid CEK length: " + rawCEK.length + ", expected 32 bytes");
        }

        // 2. Encrypt CEK với public key của user (OpenPGP)
        byte[] wrappedCek = wrapCEKWithPublicKey(rawCEK, userPublicKey);

        // 3. Lưu DocumentKey vào database
        saveDocumentKey(version.versionId, userId, wrappedCek);
    }

    /**
     * Decrypt CEK master từ Vault Transit
     */
    private String decryptFromVault(String wrappedCEKMaster) throws Exception {
        // wrappedCEKMaster format: "vault:v1:xxxxx"
        String ciphertext = wrappedCEKMaster.replaceFirst("^vault:", "");

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = vaultUrl + "/v1/transit/decrypt/" + vaultTransitKeyName;
            HttpPost post = new HttpPost(url);
            post.setHeader("X-Vault-Token", vaultToken);
            post.setHeader("Content-Type", "application/json");

            String jsonBody = "{\"ciphertext\":\"" + ciphertext + "\"}";
            post.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    String errorBody = EntityUtils.toString(response.getEntity());
                    throw new RuntimeException("Vault decrypt failed: " + statusCode + " - " + errorBody);
                }

                String responseBody = EntityUtils.toString(response.getEntity());
                // Parse JSON response: {"data":{"plaintext":"base64..."}}
                // Simple JSON parsing (có thể dùng Jackson nếu cần)
                int plaintextStart = responseBody.indexOf("\"plaintext\":\"") + 14;
                int plaintextEnd = responseBody.indexOf("\"", plaintextStart);
                return responseBody.substring(plaintextStart, plaintextEnd);
            }
        }
    }

    /**
     * Lấy public key của user từ UserService
     * API: POST /api/keys/public-key/get
     * Body: {"userId": "...", "keyType": "openpgp-cv25519"}
     */
    private String getUserPublicKey(String userId) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            String url = userServiceUrl + "/api/keys/public-key/get";
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");

            // Request body: {"userId": "...", "keyType": "openpgp-cv25519"}
            String jsonBody = "{\"userId\":\"" + userId + "\",\"keyType\":\"openpgp-cv25519\"}";
            post.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    String errorBody = EntityUtils.toString(response.getEntity());
                    throw new RuntimeException("Failed to get user public key: " + statusCode + " - " + errorBody);
                }

                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                // Parse JSON response: {"status": "OK", "data": {"publicKeyArmored": "..."}}
                // Simple JSON parsing
                int dataStart = responseBody.indexOf("\"data\":");
                if (dataStart == -1) {
                    throw new RuntimeException("Invalid response format: " + responseBody);
                }

                int publicKeyStart = responseBody.indexOf("\"publicKeyArmored\":\"", dataStart);
                if (publicKeyStart == -1) {
                    // Có thể data là null nếu không tìm thấy key
                    throw new RuntimeException("No public key found for user: " + userId);
                }

                publicKeyStart += "\"publicKeyArmored\":\"".length();
                int publicKeyEnd = responseBody.indexOf("\"", publicKeyStart);
                return responseBody.substring(publicKeyStart, publicKeyEnd);
            }
        }
    }

    /**
     * Wrap CEK với public key của user (OpenPGP)
     */
    private byte[] wrapCEKWithPublicKey(byte[] cekBytes, String publicKeyArmored) throws Exception {
        // Parse public key
        PGPPublicKeyRingCollection keyRings;
        try (InputStream in = new ArmoredInputStream(new java.io.ByteArrayInputStream(publicKeyArmored.getBytes(StandardCharsets.UTF_8)))) {
            keyRings = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(in), new BcKeyFingerprintCalculator());
        }

        PGPPublicKey encKey = null;
        Iterator<PGPPublicKeyRing> keyRingIterator = keyRings.getKeyRings();
        while (keyRingIterator.hasNext() && encKey == null) {
            PGPPublicKeyRing keyRing = keyRingIterator.next();
            Iterator<PGPPublicKey> keyIterator = keyRing.getPublicKeys();
            while (keyIterator.hasNext()) {
                PGPPublicKey key = keyIterator.next();
                if (key.isEncryptionKey()) {
                    encKey = key;
                    break;
                }
            }
        }

        if (encKey == null) {
            throw new RuntimeException("No encryption key found in public key");
        }

        // Encrypt CEK
        JcePGPDataEncryptorBuilder dataEncryptor = new JcePGPDataEncryptorBuilder(PGPEncryptedData.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(new java.security.SecureRandom())
                .setProvider("BC");

        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(dataEncryptor);
        encGen.addMethod(new JcePublicKeyKeyEncryptionMethodGenerator(encKey).setProvider("BC"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStream cOut = encGen.open(out, new byte[8192])) {
            PGPLiteralDataGenerator lData = new PGPLiteralDataGenerator();
            try (OutputStream pOut = lData.open(cOut, PGPLiteralData.BINARY, "_CEK", 32, new java.util.Date())) {
                pOut.write(cekBytes, 0, 32);
            }
        }

        return out.toByteArray();
    }

    /**
     * Lưu DocumentKey vào database
     */
    private void saveDocumentKey(UUID versionId, String userId, byte[] wrappedCek) throws SQLException {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            String sql = "INSERT INTO document_keys (id, document_version_id, recipient_id, wrapped_cek, algorithm, created_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, ?, 'OPENPGP_AES256', ?) " +
                    "ON CONFLICT (document_version_id, recipient_id) DO UPDATE SET wrapped_cek = EXCLUDED.wrapped_cek, algorithm = EXCLUDED.algorithm";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, versionId);
                stmt.setString(2, userId);
                stmt.setBytes(3, wrappedCek);
                stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                stmt.executeUpdate();
            }
        }
    }

    /**
     * Inner class để lưu version info
     */
    private static class VersionCEK {
        final UUID versionId;
        final String wrappedCEKMaster;

        VersionCEK(UUID versionId, String wrappedCEKMaster) {
            this.versionId = versionId;
            this.wrappedCEKMaster = wrappedCEKMaster;
        }
    }
}

