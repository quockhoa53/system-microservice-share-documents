package com.system_share_documents.EncryptDocumentKeysForNewMember.service;

import com.system_share_documents.EncryptDocumentKeysForNewMember.entity.MemberJoinedGroupEvent;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

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
    private final int httpConnectTimeout;
    private final int httpSocketTimeout;
    private final int maxRetries;
    private final KeycloakTokenService keycloakTokenService;
    private final ObjectMapper objectMapper;

    public DocumentKeyEncryptionService(Properties props) {
        this.dbUrl = props.getProperty("db.url");
        this.dbUser = props.getProperty("db.user");
        this.dbPassword = props.getProperty("db.password");
        this.userServiceUrl = props.getProperty("user.service.url");
        this.vaultUrl = props.getProperty("vault.url");
        this.vaultToken = props.getProperty("vault.token");
        this.vaultTransitKeyName = props.getProperty("vault.transit.key.name", "document-cek-key");

        // Validate required configuration
        if (this.userServiceUrl == null || this.userServiceUrl.isBlank()) {
            throw new RuntimeException("user.service.url is required in application.properties");
        }
        if (this.vaultUrl == null || this.vaultUrl.isBlank()) {
            throw new RuntimeException("vault.url is required in application.properties");
        }
        if (this.vaultToken == null || this.vaultToken.isBlank()) {
            throw new RuntimeException("vault.token is required in application.properties");
        }

        // HTTP timeout configuration (milliseconds)
        this.httpConnectTimeout = Integer.parseInt(props.getProperty("http.connect.timeout", "5000"));
        this.httpSocketTimeout = Integer.parseInt(props.getProperty("http.socket.timeout", "10000"));
        this.maxRetries = Integer.parseInt(props.getProperty("http.max.retries", "3"));

        // Log configuration (masked token for security)
        System.out.println("=================================================================");
        System.out.println("[INIT] DocumentKeyEncryptionService Configuration:");
        System.out.println("[INIT] Vault URL: " + this.vaultUrl);
        System.out.println("[INIT] Transit Key Name: " + this.vaultTransitKeyName);
        System.out.println("[INIT] Token length: " + (this.vaultToken != null ? this.vaultToken.length() : 0) + " chars");
        System.out.println("[INIT] Token preview: " +
                (this.vaultToken != null && this.vaultToken.length() > 20 ?
                        this.vaultToken.substring(0, 20) + "..." :
                        (this.vaultToken != null ? "***" : "null")));
        System.out.println("[INIT] User Service URL: " + this.userServiceUrl);
        System.out.println("[INIT] Max Retries: " + this.maxRetries);
        System.out.println("[INIT] HTTP Connect Timeout: " + this.httpConnectTimeout + " ms");
        System.out.println("[INIT] HTTP Socket Timeout: " + this.httpSocketTimeout + " ms");
        System.out.println("=================================================================");

        // Initialize Keycloak token service
        KeycloakTokenService tokenService = null;
        try {
            tokenService = new KeycloakTokenService(props);
        } catch (Exception e) {
            System.err.println("Failed to initialize KeycloakTokenService: " + e.getMessage());
        }
        this.keycloakTokenService = tokenService;

        // Initialize Jackson ObjectMapper for JSON parsing
        this.objectMapper = new ObjectMapper();

        // Load PostgreSQL JDBC driver explicitly
        loadPostgreSQLDriver();
    }

    /**
     * Tạo HTTP client với timeout configuration
     */
    private CloseableHttpClient createHttpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(httpConnectTimeout)
                .setSocketTimeout(httpSocketTimeout)
                .setConnectionRequestTimeout(httpConnectTimeout)
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * Load PostgreSQL JDBC driver explicitly
     */
    private void loadPostgreSQLDriver() {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            try {
                java.util.ServiceLoader<java.sql.Driver> drivers = java.util.ServiceLoader.load(java.sql.Driver.class);
                boolean driverFound = false;
                for (java.sql.Driver driver : drivers) {
                    if (driver.getClass().getName().equals("org.postgresql.Driver")) {
                        driverFound = true;
                        break;
                    }
                }
                if (!driverFound) {
                    throw new RuntimeException("PostgreSQL JDBC driver not found. Please ensure postgresql dependency is included in the JAR.");
                }
            } catch (Exception ex) {
                throw new RuntimeException("PostgreSQL JDBC driver not found. Please ensure postgresql dependency is included in the JAR.", ex);
            }
        }
    }

    /**
     * Mã hóa CEK cho member mới với tất cả documents trong group
     */
    public void encryptCEKForNewMember(MemberJoinedGroupEvent event) throws Exception {
        if (event == null) {
            throw new IllegalArgumentException("MemberJoinedGroupEvent cannot be null");
        }

        String groupId = event.getGroupId();
        String userId = event.getUserId();

        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("GroupId cannot be null or empty");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be null or empty");
        }

        System.out.println("[ENCRYPT_CEK] Step 1: Starting to get documents in group - GroupId: " + groupId);
        // 1. Lấy danh sách documents trong group từ database
        long startTime = System.currentTimeMillis();
        List<UUID> documentIds = getDocumentsInGroup(groupId);
        long endTime = System.currentTimeMillis();
        System.out.println("[ENCRYPT_CEK] Step 1: Retrieved " + documentIds.size() + " documents from group " + groupId + " in " + (endTime - startTime) + " ms");

        if (documentIds.isEmpty()) {
            System.out.println("[ENCRYPT_CEK] No documents found in group " + groupId + ", exiting process");
            return;
        }

        System.out.println("[ENCRYPT_CEK] Step 2: Starting to get public key for user - UserId: " + userId);
        // 2. Lấy public key của user mới
        startTime = System.currentTimeMillis();
        String userPublicKey = getUserPublicKey(userId);
        endTime = System.currentTimeMillis();

        if (userPublicKey == null || userPublicKey.isBlank()) {
            System.err.println("[ENCRYPT_CEK] Step 2: FAILED - User public key is null or empty for user: " + userId);
            throw new RuntimeException("User public key not found for user: " + userId);
        }
        System.out.println("[ENCRYPT_CEK] Step 2: SUCCESS - Retrieved public key for user " + userId + " (length: " + userPublicKey.length() + " chars) in " + (endTime - startTime) + " ms");

        // 3. Với mỗi document, mã hóa CEK cho tất cả versions
        System.out.println("[ENCRYPT_CEK] Step 3: Starting to encrypt CEK for " + documentIds.size() + " documents");
        int successCount = 0;
        int skipCount = 0;
        int errorCount = 0;
        int documentIndex = 0;

        for (UUID documentId : documentIds) {
            documentIndex++;
            System.out.println("[ENCRYPT_CEK] Processing document " + documentIndex + "/" + documentIds.size() + " - DocumentId: " + documentId);

            try {
                System.out.println("[ENCRYPT_CEK] Getting versions for document: " + documentId);
                List<VersionCEK> versions = getDocumentVersions(documentId);
                System.out.println("[ENCRYPT_CEK] Found " + versions.size() + " versions for document: " + documentId);

                if (versions.isEmpty()) {
                    System.err.println("[ENCRYPT_CEK] WARNING - No valid versions found for document " + documentId +
                            " (all versions may have null/empty wrappedCEKMaster or incorrect format)");
                    System.err.println("[ENCRYPT_CEK] This document cannot have its CEK encrypted for the new member until versions have valid vault: wrappedCEKMaster");
                }

                for (VersionCEK version : versions) {
                    try {
                        System.out.println("[ENCRYPT_CEK] Processing version: " + version.versionId + " for user: " + userId);

                        boolean exists = documentKeyExists(version.versionId, userId);
                        if (exists) {
                            System.out.println("[ENCRYPT_CEK] DocumentKey already exists for version " + version.versionId + " and user " + userId + ", skipping...");
                            skipCount++;
                            continue;
                        }

                        System.out.println("[ENCRYPT_CEK] Encrypting CEK for version: " + version.versionId);
                        encryptCEKForVersion(version, userId, userPublicKey);
                        System.out.println("[ENCRYPT_CEK] SUCCESS - Encrypted and saved CEK for version " + version.versionId);
                        successCount++;
                    } catch (InvalidCiphertextFormatException e) {
                        // Invalid format - skip gracefully (không tăng errorCount vì đây là data issue, không phải code error)
                        System.err.println("[ENCRYPT_CEK] SKIPPED - Version " + version.versionId + " has invalid Vault ciphertext format");
                        System.err.println("[ENCRYPT_CEK] Reason: " + e.getMessage());
                        System.err.println("[ENCRYPT_CEK] Action: This version will be skipped. Please update wrapped_cek_master in database with valid Vault ciphertext.");
                        skipCount++;
                    } catch (Exception e) {
                        System.err.println("[ENCRYPT_CEK] ERROR - Failed to encrypt CEK for version " + version.versionId + ", user " + userId + ": " + e.getMessage());
                        e.printStackTrace();
                        errorCount++;
                    }
                }
            } catch (Exception e) {
                System.err.println("[ENCRYPT_CEK] ERROR - Failed to process document " + documentId + ": " + e.getMessage());
                e.printStackTrace();
                errorCount++;
            }
        }

        System.out.println("=================================================================");
        System.out.println("[ENCRYPT_CEK] SUMMARY - Encryption process completed");
        System.out.println("[ENCRYPT_CEK] GroupId: " + groupId);
        System.out.println("[ENCRYPT_CEK] UserId: " + userId);
        System.out.println("[ENCRYPT_CEK] Total documents processed: " + documentIds.size());
        System.out.println("[ENCRYPT_CEK] Success: " + successCount);
        System.out.println("[ENCRYPT_CEK] Skipped: " + skipCount);
        System.out.println("[ENCRYPT_CEK] Errors: " + errorCount);
        System.out.println("=================================================================");
    }

    /**
     * Lấy danh sách document IDs trong group
     */
    private List<UUID> getDocumentsInGroup(String groupId) throws SQLException {
        List<UUID> documentIds = new ArrayList<>();

        System.out.println("[DB_QUERY] Executing query to get documents in group: " + groupId);
        System.out.println("[DB_QUERY] Database URL: " + dbUrl);

        long startTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            long connectTime = System.currentTimeMillis();
            System.out.println("[DB_QUERY] SUCCESS - Connected to database in " + (connectTime - startTime) + " ms");

            String sql = "SELECT DISTINCT document_id FROM group_documents WHERE group_id = ? AND deleted_at IS NULL";
            System.out.println("[DB_QUERY] SQL: " + sql);
            System.out.println("[DB_QUERY] Parameters: groupId=" + groupId);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, groupId);
                long queryStartTime = System.currentTimeMillis();

                try (ResultSet rs = stmt.executeQuery()) {
                    long queryEndTime = System.currentTimeMillis();
                    System.out.println("[DB_QUERY] Query executed in " + (queryEndTime - queryStartTime) + " ms");

                    int rowCount = 0;
                    while (rs.next()) {
                        UUID docId = (UUID) rs.getObject("document_id");
                        documentIds.add(docId);
                        rowCount++;
                    }
                    System.out.println("[DB_QUERY] SUCCESS - Retrieved " + rowCount + " document IDs");
                    if (rowCount > 0) {
                        System.out.println("[DB_QUERY] First document ID: " + documentIds.get(0));
                        if (rowCount > 1) {
                            System.out.println("[DB_QUERY] Last document ID: " + documentIds.get(documentIds.size() - 1));
                        }
                    }
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("[DB_QUERY] Total time for getDocumentsInGroup: " + (endTime - startTime) + " ms");
        } catch (SQLException e) {
            System.err.println("[DB_QUERY] ERROR - Failed to get documents in group " + groupId + ": " + e.getMessage());
            System.err.println("[DB_QUERY] SQL State: " + e.getSQLState());
            System.err.println("[DB_QUERY] Error Code: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }

        return documentIds;
    }

    /**
     * Lấy tất cả versions của document với wrappedCEKMaster
     */
    private List<VersionCEK> getDocumentVersions(UUID documentId) throws SQLException {
        List<VersionCEK> versions = new ArrayList<>();

        System.out.println("[DB_QUERY] Executing query to get versions for document: " + documentId);

        long startTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            long connectTime = System.currentTimeMillis();
            System.out.println("[DB_QUERY] Connected to database in " + (connectTime - startTime) + " ms");

            // Query giống DocumentService: kiểm tra deleted_at IS NULL thay vì status = 'AVAILABLE'
            // CAST wrapped_cek_master thành TEXT để đảm bảo đọc đúng giá trị (tránh trả về Long)
            String sql = "SELECT id, wrapped_cek_master FROM document_versions WHERE document_id = ? AND deleted_at IS NULL";
            System.out.println("[DB_QUERY] SQL: " + sql);
            System.out.println("[DB_QUERY] Parameters: documentId=" + documentId);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, documentId);
                long queryStartTime = System.currentTimeMillis();

                try (ResultSet rs = stmt.executeQuery()) {
                    long queryEndTime = System.currentTimeMillis();
                    System.out.println("[DB_QUERY] Query executed in " + (queryEndTime - queryStartTime) + " ms");

                    int rowCount = 0;
                    int validCount = 0;
                    int skippedCount = 0;

                    while (rs.next()) {
                        rowCount++;
                        UUID versionId = (UUID) rs.getObject("id");

                        // Đọc wrapped_cek_master - cột này là kiểu OID (PostgreSQL Large Object)
                        // JPA/Hibernate tự động xử lý OID, nhưng raw JDBC cần dùng Large Object API
                        Object wrappedCEKMasterObj = null;

                        try {
                            // Thử đọc như String trước (nếu driver tự động convert)
                            String str = rs.getString("wrapped_cek_master");
                            if (str != null && !str.isEmpty()) {
                                wrappedCEKMasterObj = str;
                                System.out.println("[DB_QUERY] Read wrapped_cek_master as String (length: " + str.length() + " chars)");
                            }
                        } catch (SQLException e) {
                            System.out.println("[DB_QUERY] Cannot read as String, trying OID (Large Object): " + e.getMessage());

                            // Đọc OID value (Long) và sử dụng Large Object API
                            try {
                                Object oidObj = rs.getObject("wrapped_cek_master");

                                if (oidObj instanceof Long) {
                                    long oid = (Long) oidObj;
                                    System.out.println("[DB_QUERY] Read wrapped_cek_master as OID: " + oid);

                                    // Sử dụng PostgreSQL Large Object API để đọc nội dung
                                    LargeObjectManager lom = ((org.postgresql.PGConnection) conn.unwrap(org.postgresql.PGConnection.class)).getLargeObjectAPI();
                                    LargeObject lo = lom.open(oid, LargeObjectManager.READ);

                                    try {
                                        // Đọc toàn bộ nội dung từ Large Object
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                        byte[] buffer = new byte[4096];
                                        int bytesRead;
                                        // LargeObject.read() cần 3 parameters: buffer, offset, length
                                        while ((bytesRead = lo.read(buffer, 0, buffer.length)) > 0) {
                                            baos.write(buffer, 0, bytesRead);
                                        }

                                        // Convert bytes thành String (UTF-8)
                                        String content = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                                        wrappedCEKMasterObj = content;
                                        System.out.println("[DB_QUERY] Read Large Object content successfully (length: " + content.length() + " chars)");
                                    } finally {
                                        lo.close();
                                    }
                                } else if (oidObj instanceof java.sql.Clob) {
                                    // Nếu driver tự động convert OID thành Clob
                                    wrappedCEKMasterObj = oidObj;
                                    System.out.println("[DB_QUERY] Read wrapped_cek_master as Clob (auto-converted from OID)");
                                } else {
                                    wrappedCEKMasterObj = oidObj;
                                    System.out.println("[DB_QUERY] Read wrapped_cek_master as Object: " +
                                            (oidObj != null ? oidObj.getClass().getSimpleName() : "NULL"));
                                }
                            } catch (Exception e2) {
                                System.err.println("[DB_QUERY] ERROR - Failed to read Large Object: " + e2.getMessage());
                                e2.printStackTrace();
                                throw new RuntimeException("Failed to read wrapped_cek_master (OID) for version: " + versionId, e2);
                            }
                        }

                        System.out.println("[DB_QUERY] Processing version - VersionId: " + versionId);
                        System.out.println("[DB_QUERY] wrappedCEKMaster type: " +
                                (wrappedCEKMasterObj != null ? wrappedCEKMasterObj.getClass().getSimpleName() : "NULL"));

                        try {
                            // Materialize LOB (Clob) thành String - GIỐNG HỆT DocumentService
                            String wrappedCEKMaster = materializeLob(wrappedCEKMasterObj, versionId);

                            // Log giá trị wrappedCEKMaster với format rõ ràng - GIỐNG DocumentService debug
                            System.out.println("=================================================================");
                            System.out.println("[DB_QUERY] Materialized wrappedCEKMaster for version: " + versionId);
                            if (wrappedCEKMaster == null) {
                                System.out.println("[DB_QUERY] wrappedCEKMaster value: NULL");
                            } else if (wrappedCEKMaster.isEmpty()) {
                                System.out.println("[DB_QUERY] wrappedCEKMaster value: EMPTY STRING (length=0)");
                            } else {
                                System.out.println("[DB_QUERY] wrappedCEKMaster length: " + wrappedCEKMaster.length() + " chars");
                                System.out.println("[DB_QUERY] wrappedCEKMaster starts with 'vault:': " + wrappedCEKMaster.startsWith("vault:"));

                                // Log full value để debug (giống như user debug thấy: vault:v1:Bi3eBCvqMOqVOO8tfzGychObxvHrjwh4TDU8Ryux1THEvh/aW+N0JeBGwSmxNiLnixKdVfKKIbtIuiaJ)
                                System.out.println("[DB_QUERY] wrappedCEKMaster FULL VALUE: \"" + wrappedCEKMaster + "\"");

                                // Kiểm tra format
                                if (wrappedCEKMaster.startsWith("vault:v1:")) {
                                    String ciphertextPart = wrappedCEKMaster.substring(9);
                                    System.out.println("[DB_QUERY] ✓ Format: vault:v1: detected");
                                    System.out.println("[DB_QUERY] Ciphertext part length: " + ciphertextPart.length() + " chars");
                                    System.out.println("[DB_QUERY] Ciphertext part: \"" + ciphertextPart + "\"");
                                } else if (wrappedCEKMaster.startsWith("vault:")) {
                                    System.out.println("[DB_QUERY] ⚠️  Format: vault: detected but missing v1:");
                                } else {
                                    System.out.println("[DB_QUERY] ✗ Format: Missing 'vault:' prefix");
                                }
                            }
                            System.out.println("=================================================================");

                            // Validation GIỐNG HỆT DocumentService - nhưng skip version lỗi thay vì fail job
                            if (wrappedCEKMaster == null) {
                                skippedCount++;
                                System.err.println("=================================================================");
                                System.err.println("[DB_QUERY] ✗ SKIPPED - wrappedCEKMaster is null");
                                System.err.println("[DB_QUERY] VersionId: " + versionId);
                                System.err.println("[DB_QUERY] Reason: wrappedCEKMaster is null for version: " + versionId);
                                System.err.println("=================================================================");
                                continue;
                            }

                            if (!wrappedCEKMaster.startsWith("vault:")) {
                                skippedCount++;
                                System.err.println("=================================================================");
                                System.err.println("[DB_QUERY] ✗ SKIPPED - Invalid Vault ciphertext format");
                                System.err.println("[DB_QUERY] VersionId: " + versionId);
                                System.err.println("[DB_QUERY] Expected prefix 'vault:' but got: " +
                                        (wrappedCEKMaster.length() > 100 ? wrappedCEKMaster.substring(0, 100) + "..." : wrappedCEKMaster));
                                System.err.println("=================================================================");
                                continue;
                            }

                            // Giá trị hợp lệ - có format "vault:..."
                            versions.add(new VersionCEK(versionId, wrappedCEKMaster));
                            validCount++;
                            System.out.println("[DB_QUERY] ✓ VALID - Version added to encryption queue");
                            System.out.println("[DB_QUERY] Format: vault: prefix detected ✓");
                        } catch (RuntimeException e) {
                            skippedCount++;
                            System.err.println("=================================================================");
                            System.err.println("[DB_QUERY] ✗ SKIPPED - Error processing version");
                            System.err.println("[DB_QUERY] VersionId: " + versionId);
                            System.err.println("[DB_QUERY] Error: " + e.getMessage());
                            System.err.println("=================================================================");
                            // Continue với version tiếp theo thay vì fail toàn bộ job
                        }
                    }

                    System.out.println("[DB_QUERY] SUCCESS - Retrieved " + rowCount + " total versions, " + validCount + " valid (with vault: prefix), " + skippedCount + " skipped");
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("[DB_QUERY] Total time for getDocumentVersions: " + (endTime - startTime) + " ms");
        } catch (SQLException e) {
            System.err.println("[DB_QUERY] ERROR - Failed to get document versions for " + documentId + ": " + e.getMessage());
            System.err.println("[DB_QUERY] SQL State: " + e.getSQLState());
            System.err.println("[DB_QUERY] Error Code: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }

        return versions;
    }

    /**
     * Materialize LOB (Clob) thành String
     * GIỐNG HỆT DocumentService.materializeLob() - CODE COPY TRỰC TIẾP TỪ ShareDocumentServiceImpl
     * Đảm bảo đọc đúng dữ liệu từ Clob giống hệt DocumentService
     */
    private String materializeLob(Object wrappedCEKMasterObj, UUID versionId) {
        String raw;
        if (wrappedCEKMasterObj instanceof String) {
            System.out.println("[DB_QUERY] wrappedCEKMaster is String type");
            raw = (String) wrappedCEKMasterObj;
        } else if (wrappedCEKMasterObj instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) wrappedCEKMasterObj;
                long length = clob.length();
                System.out.println("[DB_QUERY] wrappedCEKMaster is Clob type, length: " + length + " chars");

                if (length > Integer.MAX_VALUE) {
                    throw new RuntimeException("wrappedCEKMaster too large for version: " + versionId);
                }

                // Đọc Clob bằng getSubString - GIỐNG HỆT DocumentService (line 245)
                raw = clob.getSubString(1, (int) length);
                System.out.println("[DB_QUERY] Clob content read successfully, actual length: " + raw.length() + " chars");

                // Log để debug - xem giá trị thực tế
                if (raw.length() > 0) {
                    int previewLength = Math.min(150, raw.length());
                    System.out.println("[DB_QUERY] Clob content preview (first " + previewLength + " chars): \"" + raw.substring(0, previewLength) +
                            (raw.length() > previewLength ? "..." : "") + "\"");
                    System.out.println("[DB_QUERY] Clob content starts with 'vault:': " + raw.startsWith("vault:"));
                }
            } catch (java.sql.SQLException e) {
                throw new RuntimeException("Failed to read wrappedCEKMaster for version: " + versionId, e);
            }
        } else {
            System.out.println("[DB_QUERY] wrappedCEKMaster is " + wrappedCEKMasterObj.getClass().getSimpleName() + " type, converting to String");
            raw = wrappedCEKMasterObj.toString();
        }

        // Logic trim và kiểm tra prefix - GIỐNG HỆT DocumentService (lines 253-257)
        if (raw.startsWith("vault:")) {
            String trimmed = raw.trim();
            System.out.println("[DB_QUERY] Materialized value starts with 'vault:', returning trimmed: length=" + trimmed.length() + " chars");
            return trimmed;
        }
        String trimmed = raw.trim();
        String result = trimmed.startsWith("vault:") ? trimmed : raw;
        System.out.println("[DB_QUERY] Materialized value (after trim check): length=" + result.length() + " chars, starts with 'vault:': " + result.startsWith("vault:"));
        return result;
    }

    /**
     * Kiểm tra DocumentKey đã tồn tại chưa
     */
    private boolean documentKeyExists(UUID versionId, String userId) throws SQLException {
        System.out.println("[DB_QUERY] Checking if DocumentKey exists - VersionId: " + versionId + ", UserId: " + userId);

        long startTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            String sql = "SELECT COUNT(*) FROM document_keys WHERE document_version_id = ? AND recipient_id = ?";
            System.out.println("[DB_QUERY] SQL: " + sql);
            System.out.println("[DB_QUERY] Parameters: versionId=" + versionId + ", userId=" + userId);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, versionId);
                stmt.setString(2, userId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        boolean exists = count > 0;
                        long endTime = System.currentTimeMillis();

                        if (exists) {
                            System.out.println("[DB_QUERY] DocumentKey EXISTS (count=" + count + ") for version " + versionId + ", user " + userId + " in " + (endTime - startTime) + " ms");
                        } else {
                            System.out.println("[DB_QUERY] DocumentKey DOES NOT EXIST (count=" + count + ") for version " + versionId + ", user " + userId + " in " + (endTime - startTime) + " ms");
                        }

                        return exists;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB_QUERY] ERROR - Failed to check DocumentKey existence: " + e.getMessage());
            System.err.println("[DB_QUERY] SQL State: " + e.getSQLState());
            System.err.println("[DB_QUERY] Error Code: " + e.getErrorCode());
            e.printStackTrace();
            throw e;
        }

        System.out.println("[DB_QUERY] No result returned, assuming DocumentKey does not exist");
        return false;
    }

    /**
     * Mã hóa CEK cho một version
     */
    private void encryptCEKForVersion(VersionCEK version, String userId, String userPublicKey) throws Exception {
        System.out.println("[ENCRYPT_VERSION] Starting encryption for version: " + version.versionId);

        // 1. Decrypt CEK master từ Vault
        System.out.println("[ENCRYPT_VERSION] Step 1: Decrypting CEK from Vault");
        System.out.println("[ENCRYPT_VERSION] WrappedCEKMaster prefix: " +
                (version.wrappedCEKMaster != null && version.wrappedCEKMaster.length() > 20 ?
                        version.wrappedCEKMaster.substring(0, 20) + "..." : version.wrappedCEKMaster));

        long startTime = System.currentTimeMillis();
        String rawCEKBase64 = decryptFromVault(version.wrappedCEKMaster);
        long decryptTime = System.currentTimeMillis();
        System.out.println("[ENCRYPT_VERSION] Step 1: SUCCESS - Decrypted CEK from Vault in " + (decryptTime - startTime) + " ms");
        System.out.println("[ENCRYPT_VERSION] RawCEKBase64 length: " + (rawCEKBase64 != null ? rawCEKBase64.length() : 0) + " chars");

        byte[] rawCEK = Base64.getDecoder().decode(rawCEKBase64);
        System.out.println("[ENCRYPT_VERSION] Decoded CEK length: " + rawCEK.length + " bytes");

        if (rawCEK.length != 32) {
            System.err.println("[ENCRYPT_VERSION] ERROR - Invalid CEK length: " + rawCEK.length + ", expected 32 bytes");
            throw new RuntimeException("Invalid CEK length: " + rawCEK.length + ", expected 32 bytes");
        }

        // 2. Encrypt CEK với public key của user (OpenPGP)
        System.out.println("[ENCRYPT_VERSION] Step 2: Encrypting CEK with user public key");
        long encryptStartTime = System.currentTimeMillis();
        byte[] wrappedCek = wrapCEKWithPublicKey(rawCEK, userPublicKey);
        long encryptEndTime = System.currentTimeMillis();
        System.out.println("[ENCRYPT_VERSION] Step 2: SUCCESS - Encrypted CEK with public key in " + (encryptEndTime - encryptStartTime) + " ms");
        System.out.println("[ENCRYPT_VERSION] Wrapped CEK length: " + wrappedCek.length + " bytes");

        // 3. Lưu DocumentKey vào database
        System.out.println("[ENCRYPT_VERSION] Step 3: Saving DocumentKey to database");
        long saveStartTime = System.currentTimeMillis();
        saveDocumentKey(version.versionId, userId, wrappedCek);
        long saveEndTime = System.currentTimeMillis();
        System.out.println("[ENCRYPT_VERSION] Step 3: SUCCESS - Saved DocumentKey to database in " + (saveEndTime - saveStartTime) + " ms");

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("[ENCRYPT_VERSION] COMPLETED - Total time for version " + version.versionId + ": " + totalTime + " ms");
    }

    /**
     * Decrypt CEK master từ Vault Transit với retry logic
     *
     * GỬI NGUYÊN GIÁ TRỊ TỪ DB - KHÔNG PREPEND PREFIX
     * DocumentService hoạt động OK với giá trị "43466", vậy Vault chấp nhận format này
     */
    private String decryptFromVault(String wrappedCEKMaster) throws Exception {
        if (wrappedCEKMaster == null || wrappedCEKMaster.trim().isEmpty()) {
            throw new IllegalArgumentException("wrappedCEKMaster is null or empty");
        }

        // GỬI NGUYÊN GIÁ TRỊ TỪ DB - KHÔNG THAY ĐỔI GÌ CẢ
        String ciphertext = wrappedCEKMaster.trim();
        System.out.println("[VAULT_DECRYPT] Original value from DB: \"" + ciphertext + "\"");
        System.out.println("[VAULT_DECRYPT] Sending value AS-IS to Vault (no prefix manipulation)");
        System.out.println("[VAULT_DECRYPT] Ciphertext length: " + ciphertext.length() + " chars");
        System.out.println("[VAULT_DECRYPT] Vault URL: " + vaultUrl);
        System.out.println("[VAULT_DECRYPT] Transit key name: " + vaultTransitKeyName);
        System.out.println("[VAULT_DECRYPT] Max retries: " + maxRetries);

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            System.out.println("[VAULT_DECRYPT] Attempt " + attempt + "/" + maxRetries);
            long attemptStartTime = System.currentTimeMillis();

            try {
                String plaintext = callVaultDecrypt(ciphertext);
                long totalTime = System.currentTimeMillis() - attemptStartTime;
                System.out.println("[VAULT_DECRYPT] SUCCESS - Decrypted in " + totalTime + " ms (attempt " + attempt + ")");
                System.out.println("[VAULT_DECRYPT] Plaintext length: " + plaintext.length() + " chars");
                return plaintext;
            } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                lastException = e;
                long attemptTime = System.currentTimeMillis() - attemptStartTime;
                System.err.println("[VAULT_DECRYPT] ERROR - Connection/Timeout error on attempt " + attempt + " after " + attemptTime + " ms: " + e.getMessage());

                if (attempt < maxRetries) {
                    long backoffTime = 1000 * attempt;
                    System.out.println("[VAULT_DECRYPT] Retrying after " + backoffTime + " ms...");
                    Thread.sleep(backoffTime); // Exponential backoff
                } else {
                    System.err.println("[VAULT_DECRYPT] FAILED - Exhausted all " + maxRetries + " attempts");
                    throw new RuntimeException("Failed to connect to Vault after " + maxRetries +
                            " attempts. URL: " + vaultUrl + ". Please ensure Vault is running.", e);
                }
            } catch (RuntimeException e) {
                // Kiểm tra nếu là lỗi base64 decode - không retry vì sẽ luôn fail
                if (e.getMessage() != null && e.getMessage().contains("could not decode base64")) {
                    System.err.println("[VAULT_DECRYPT] Base64 decode error detected - this is not a retryable error");
                    throw new InvalidCiphertextFormatException("Invalid Vault ciphertext: value \"" + ciphertext +
                            "\" is not a valid base64-encoded ciphertext. " +
                            "This may be an ID/reference or legacy format that cannot be decrypted.");
                }

                lastException = e;
                System.err.println("[VAULT_DECRYPT] ERROR - Unexpected error on attempt " + attempt + ": " + e.getMessage());
                e.printStackTrace();

                if (attempt < maxRetries) {
                    long backoffTime = 1000 * attempt;
                    System.out.println("[VAULT_DECRYPT] Retrying after " + backoffTime + " ms...");
                    Thread.sleep(backoffTime);
                } else {
                    throw e;
                }
            } catch (Exception e) {
                lastException = e;
                System.err.println("[VAULT_DECRYPT] ERROR - Unexpected error on attempt " + attempt + ": " + e.getMessage());
                e.printStackTrace();

                if (attempt < maxRetries) {
                    long backoffTime = 1000 * attempt;
                    System.out.println("[VAULT_DECRYPT] Retrying after " + backoffTime + " ms...");
                    Thread.sleep(backoffTime);
                } else {
                    throw e;
                }
            }
        }

        System.err.println("[VAULT_DECRYPT] FAILED - All attempts exhausted");
        throw new RuntimeException("Failed to decrypt from Vault", lastException);
    }

    /**
     * HTTP DECRYPT CHUẨN - Gửi nguyên giá trị ciphertext từ DB
     */
    private String callVaultDecrypt(String ciphertext) throws Exception {
        HttpURLConnection connection = null;

        try {
            // Normalize URL
            String normalizedVaultUrl = vaultUrl.trim();
            if (normalizedVaultUrl.endsWith("/")) {
                normalizedVaultUrl = normalizedVaultUrl.substring(0, normalizedVaultUrl.length() - 1);
            }

            String urlString = normalizedVaultUrl + "/v1/transit/decrypt/" + vaultTransitKeyName;
            URL url = new URL(urlString);

            System.out.println("[VAULT_DECRYPT] Request URL: " + urlString);

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(httpConnectTimeout);
            connection.setReadTimeout(httpSocketTimeout);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("X-Vault-Token", vaultToken);

            // Gửi ciphertext NGUYÊN VĂN (không escape, không prepend prefix)
            // Escape chỉ cho ký tự JSON đặc biệt
            String escapedCiphertext = ciphertext.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            String body = "{\"ciphertext\":\"" + escapedCiphertext + "\"}";

            System.out.println("[VAULT_DECRYPT] Request body: " + body);
            System.out.println("[VAULT_DECRYPT] Request body length: " + body.length() + " chars");
            System.out.println("[VAULT_DECRYPT] Ciphertext value (raw): \"" + ciphertext + "\"");
            System.out.println("[VAULT_DECRYPT] Ciphertext value (escaped): \"" + escapedCiphertext + "\"");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            System.out.println("[VAULT_DECRYPT] Response status code: " + status);

            InputStream is = (status >= 200 && status < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String response = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            if (status != 200) {
                System.err.println("=================================================================");
                System.err.println("[VAULT_DECRYPT] ERROR - Decrypt failed");
                System.err.println("[VAULT_DECRYPT] Status code: " + status);
                System.err.println("[VAULT_DECRYPT] Error response: " + response);
                System.err.println("[VAULT_DECRYPT] Request URL: " + urlString);
                System.err.println("[VAULT_DECRYPT] Transit key name: " + vaultTransitKeyName);
                System.err.println("[VAULT_DECRYPT] Ciphertext: \"" + ciphertext + "\"");
                System.err.println("[VAULT_DECRYPT] Ciphertext length: " + ciphertext.length() + " chars");
                System.err.println("[VAULT_DECRYPT] Token length: " + (vaultToken != null ? vaultToken.length() : 0));

                if (status == 400 && response.contains("could not decode base64")) {
                    System.err.println("[VAULT_DECRYPT] ⚠️  400 Bad Request - Base64 decode error");
                    System.err.println("[VAULT_DECRYPT] The ciphertext value may not be a valid Vault Transit ciphertext");
                    System.err.println("[VAULT_DECRYPT] Expected format: vault:v1:base64encoded (e.g., vault:v1:qniNVudFXhCR8TgG657agSO+...)");
                    System.err.println("[VAULT_DECRYPT] Actual value: \"" + ciphertext + "\"");
                    System.err.println("[VAULT_DECRYPT] This suggests the value in database may be:");
                    System.err.println("[VAULT_DECRYPT] 1. An ID/reference instead of actual ciphertext");
                    System.err.println("[VAULT_DECRYPT] 2. A legacy format that needs conversion");
                    System.err.println("[VAULT_DECRYPT] 3. Corrupted or incomplete data");
                } else if (status == 403) {
                    System.err.println("[VAULT_DECRYPT] ⚠️  403 Permission Denied - Possible causes:");
                    System.err.println("[VAULT_DECRYPT] 1. Token does not have 'update' permission on path 'transit/decrypt/" + vaultTransitKeyName + "'");
                    System.err.println("[VAULT_DECRYPT] 2. Token policy does not include required capabilities");
                    System.err.println("[VAULT_DECRYPT] 3. Transit key name '" + vaultTransitKeyName + "' may not exist");
                    System.err.println("[VAULT_DECRYPT] 4. Token may be expired or invalid");
                }
                System.err.println("=================================================================");
                throw new RuntimeException("Vault decrypt failed. HTTP " + status + " - " + response);
            }

            // Parse JSON response bằng Jackson ObjectMapper
            JsonNode root = objectMapper.readTree(response);
            JsonNode plaintextNode = root.path("data").path("plaintext");

            if (plaintextNode.isMissingNode() || plaintextNode.isNull()) {
                System.err.println("[VAULT_DECRYPT] ERROR - Response missing plaintext");
                System.err.println("[VAULT_DECRYPT] Response body: " + response);
                throw new RuntimeException("Vault decrypt response missing plaintext: " + response);
            }

            String plaintext = plaintextNode.asText();
            System.out.println("[VAULT_DECRYPT] Plaintext extracted successfully, length: " + plaintext.length() + " chars");

            return plaintext;

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Lấy public key của user từ UserService với retry logic
     * API: POST /api/keys/public-key/get
     * Body: {"userId": "...", "keyType": "openpgp-cv25519"}
     */
    private String getUserPublicKey(String userId) throws Exception {
        if (userServiceUrl == null || userServiceUrl.isBlank()) {
            throw new RuntimeException("user.service.url is not configured");
        }

        System.out.println("[USER_SERVICE] Starting to get public key for user: " + userId);
        System.out.println("[USER_SERVICE] UserService URL: " + userServiceUrl);
        System.out.println("[USER_SERVICE] Max retries: " + maxRetries);

        Exception lastException = null;
        boolean tokenInvalidated = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            System.out.println("[USER_SERVICE] Attempt " + attempt + "/" + maxRetries);
            long attemptStartTime = System.currentTimeMillis();

            try (CloseableHttpClient httpClient = createHttpClient()) {
                String url = userServiceUrl + "/api/keys/public-key/get";
                System.out.println("[USER_SERVICE] Request URL: " + url);

                HttpPost post = new HttpPost(url);
                post.setHeader("Content-Type", "application/json");

                // Thêm Authorization header với Bearer token từ Keycloak
                // Nếu token đã bị invalidate ở lần trước, đảm bảo lấy token mới
                if (keycloakTokenService != null) {
                    try {
                        System.out.println("[USER_SERVICE] Getting Keycloak token...");
                        // Force refresh token nếu đã bị invalidate ở lần retry trước
                        if (tokenInvalidated) {
                            System.out.println("[USER_SERVICE] Token was invalidated, forcing refresh...");
                            keycloakTokenService.invalidateToken();
                        }
                        String token = keycloakTokenService.getAccessToken();
                        if (token == null || token.trim().isEmpty()) {
                            System.err.println("[USER_SERVICE] ERROR - Token is null or empty");
                            throw new RuntimeException("Token is null or empty");
                        }

                        // Ensure token is trimmed and properly formatted
                        token = token.trim();

                        // Set Authorization header with Bearer prefix
                        String authHeader = "Bearer " + token;
                        post.setHeader("Authorization", authHeader);

                        System.out.println("[USER_SERVICE] SUCCESS - Got Keycloak token (length: " + token.length() + " chars)");
                        System.out.println("[USER_SERVICE] Token preview: " +
                                (token.length() > 50 ? token.substring(0, 50) + "..." : token));
                    } catch (Exception e) {
                        System.err.println("[USER_SERVICE] ERROR - Failed to get Keycloak token: " + e.getMessage());
                        e.printStackTrace();
                        // Nếu không lấy được token, vẫn thử gọi API (có thể không cần auth)
                    }
                } else {
                    System.out.println("[USER_SERVICE] WARNING - KeycloakTokenService is null, proceeding without token");
                }

                // Request body: {"userId": "...", "keyType": "openpgp-cv25519"}
                String jsonBody = "{\"userId\":\"" + userId + "\",\"keyType\":\"openpgp-cv25519\"}";
                System.out.println("[USER_SERVICE] Request body: " + jsonBody);
                post.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    long responseTime = System.currentTimeMillis();
                    int statusCode = response.getStatusLine().getStatusCode();
                    System.out.println("[USER_SERVICE] Response received in " + (responseTime - attemptStartTime) + " ms");
                    System.out.println("[USER_SERVICE] Status code: " + statusCode);

                    if (statusCode == 401) {
                        // Token có thể hết hạn hoặc không hợp lệ, invalidate và retry
                        String errorBody = "";
                        try {
                            errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            errorBody = "Unable to read error response body";
                        }

                        // Log detailed error information
                        System.err.println("=================================================================");
                        System.err.println("[USER_SERVICE] ERROR - 401 Unauthorized");
                        System.err.println("[USER_SERVICE] Attempt: " + attempt + "/" + maxRetries);
                        System.err.println("[USER_SERVICE] URL: " + url);
                        System.err.println("[USER_SERVICE] UserId: " + userId);
                        if (keycloakTokenService != null) {
                            try {
                                String currentToken = keycloakTokenService.getAccessToken();
                                System.err.println("[USER_SERVICE] Token being used (preview): " +
                                        (currentToken != null && currentToken.length() > 50 ?
                                                currentToken.substring(0, 50) + "..." : currentToken));
                            } catch (Exception e) {
                                System.err.println("[USER_SERVICE] Could not retrieve token for debugging: " + e.getMessage());
                            }
                        }
                        System.err.println("[USER_SERVICE] Error response body: " + errorBody);
                        System.err.println("=================================================================");

                        if (keycloakTokenService != null) {
                            keycloakTokenService.invalidateToken();
                            tokenInvalidated = true;
                            System.out.println("[USER_SERVICE] Token invalidated, will retry with new token");
                        }

                        if (attempt < maxRetries) {
                            long backoffTime = 500 * attempt;
                            System.out.println("[USER_SERVICE] Retrying with new token in " + backoffTime + "ms...");
                            Thread.sleep(backoffTime); // Short delay before retry
                            continue; // Retry với token mới
                        } else {
                            String errorMsg = "Unauthorized (401) after " + maxRetries + " attempts. " +
                                    "This usually means:\n" +
                                    "1. Token is invalid or expired\n" +
                                    "2. Token does not have required resource_access for 'frontend-app'\n" +
                                    "3. Token does not have required claims/roles\n" +
                                    "4. Keycloak configuration issue\n\n" +
                                    "Error response: " + errorBody + "\n\n" +
                                    "SOLUTION: Please check Keycloak configuration:\n" +
                                    "- Ensure client 'token-service' has 'frontend-app' in its client scopes\n" +
                                    "- Or configure a protocol mapper to add resource_access for frontend-app to the token";
                            throw new RuntimeException(errorMsg);
                        }
                    }
                    if (statusCode != 200) {
                        String errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        System.err.println("[USER_SERVICE] ERROR - Status code: " + statusCode);
                        System.err.println("[USER_SERVICE] Error body: " + errorBody);

                        if (statusCode >= 500 && attempt < maxRetries) {
                            long backoffTime = 1000 * attempt;
                            System.out.println("[USER_SERVICE] Server error (5xx), retrying in " + backoffTime + "ms...");
                            Thread.sleep(backoffTime);
                            continue;
                        }
                        throw new RuntimeException("Failed to get user public key: " + statusCode + " - " + errorBody);
                    }

                    String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    System.out.println("[USER_SERVICE] Response body length: " + responseBody.length() + " chars");

                    // Parse JSON response: {"status": "OK", "data": {"publicKeyArmored": "..."}}
                    // Simple JSON parsing
                    int dataStart = responseBody.indexOf("\"data\":");
                    if (dataStart == -1) {
                        System.err.println("[USER_SERVICE] ERROR - Invalid response format, cannot find 'data' field");
                        System.err.println("[USER_SERVICE] Response body: " + responseBody);
                        throw new RuntimeException("Invalid response format: " + responseBody);
                    }

                    int publicKeyStart = responseBody.indexOf("\"publicKeyArmored\":\"", dataStart);
                    if (publicKeyStart == -1) {
                        System.err.println("[USER_SERVICE] ERROR - No public key found in response");
                        System.err.println("[USER_SERVICE] Response body: " + responseBody);
                        throw new RuntimeException("No public key found for user: " + userId);
                    }

                    publicKeyStart += "\"publicKeyArmored\":\"".length();
                    int publicKeyEnd = responseBody.indexOf("\"", publicKeyStart);

                    if (publicKeyEnd <= publicKeyStart) {
                        System.err.println("[USER_SERVICE] ERROR - Invalid public key format in response");
                        throw new RuntimeException("Invalid public key format in response");
                    }

                    String publicKey = responseBody.substring(publicKeyStart, publicKeyEnd);
                    long totalTime = System.currentTimeMillis() - attemptStartTime;
                    System.out.println("[USER_SERVICE] SUCCESS - Retrieved public key in " + totalTime + " ms (attempt " + attempt + ")");
                    System.out.println("[USER_SERVICE] Public key length: " + publicKey.length() + " chars");
                    System.out.println("[USER_SERVICE] Public key preview: " +
                            (publicKey.length() > 50 ? publicKey.substring(0, 50) + "..." : publicKey));

                    return publicKey;
                }
            } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
                lastException = e;
                long attemptTime = System.currentTimeMillis() - attemptStartTime;
                System.err.println("[USER_SERVICE] ERROR - Connection/Timeout error on attempt " + attempt + " after " + attemptTime + " ms: " + e.getMessage());

                if (attempt < maxRetries) {
                    long backoffTime = 1000 * attempt;
                    System.out.println("[USER_SERVICE] Retrying after " + backoffTime + " ms...");
                    Thread.sleep(backoffTime); // Exponential backoff
                } else {
                    System.err.println("[USER_SERVICE] FAILED - Exhausted all " + maxRetries + " attempts");
                    throw new RuntimeException("Failed to connect to UserService after " + maxRetries +
                            " attempts. URL: " + userServiceUrl + ". Please ensure UserService is running.", e);
                }
            } catch (Exception e) {
                lastException = e;
                System.err.println("[USER_SERVICE] ERROR - Unexpected error on attempt " + attempt + ": " + e.getMessage());
                e.printStackTrace();

                if (attempt < maxRetries) {
                    long backoffTime = 1000 * attempt;
                    System.out.println("[USER_SERVICE] Retrying after " + backoffTime + " ms...");
                    Thread.sleep(backoffTime);
                } else {
                    throw e;
                }
            }
        }

        System.err.println("[USER_SERVICE] FAILED - All attempts exhausted");
        throw new RuntimeException("Failed to get user public key", lastException);
    }

    /**
     * Wrap CEK với public key của user (OpenPGP)
     */
    private byte[] wrapCEKWithPublicKey(byte[] cekBytes, String publicKeyArmored) throws Exception {
        System.out.println("[PGP_ENCRYPT] Starting to wrap CEK with public key");
        System.out.println("[PGP_ENCRYPT] CEK size: " + cekBytes.length + " bytes");
        System.out.println("[PGP_ENCRYPT] Public key length: " + publicKeyArmored.length() + " chars");

        long startTime = System.currentTimeMillis();

        // Parse public key
        System.out.println("[PGP_ENCRYPT] Step 1: Parsing public key");
        PGPPublicKeyRingCollection keyRings;
        try (InputStream in = new ArmoredInputStream(new java.io.ByteArrayInputStream(publicKeyArmored.getBytes(StandardCharsets.UTF_8)))) {
            keyRings = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(in), new BcKeyFingerprintCalculator());
        }
        long parseTime = System.currentTimeMillis();
        System.out.println("[PGP_ENCRYPT] Step 1: SUCCESS - Parsed public key in " + (parseTime - startTime) + " ms");

        System.out.println("[PGP_ENCRYPT] Step 2: Finding encryption key");
        PGPPublicKey encKey = null;
        int keyRingCount = 0;
        int keyCount = 0;
        Iterator<PGPPublicKeyRing> keyRingIterator = keyRings.getKeyRings();
        while (keyRingIterator.hasNext() && encKey == null) {
            keyRingCount++;
            PGPPublicKeyRing keyRing = keyRingIterator.next();
            Iterator<PGPPublicKey> keyIterator = keyRing.getPublicKeys();
            while (keyIterator.hasNext()) {
                keyCount++;
                PGPPublicKey key = keyIterator.next();
                if (key.isEncryptionKey()) {
                    encKey = key;
                    System.out.println("[PGP_ENCRYPT] Found encryption key - Key ID: " + encKey.getKeyID());
                    break;
                }
            }
        }

        if (encKey == null) {
            System.err.println("[PGP_ENCRYPT] ERROR - No encryption key found in public key");
            System.err.println("[PGP_ENCRYPT] Processed " + keyRingCount + " key rings, " + keyCount + " keys");
            throw new RuntimeException("No encryption key found in public key");
        }

        long findKeyTime = System.currentTimeMillis();
        System.out.println("[PGP_ENCRYPT] Step 2: SUCCESS - Found encryption key in " + (findKeyTime - parseTime) + " ms");

        // Encrypt CEK
        System.out.println("[PGP_ENCRYPT] Step 3: Encrypting CEK with OpenPGP");
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

        byte[] wrappedCek = out.toByteArray();
        long encryptTime = System.currentTimeMillis();
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("[PGP_ENCRYPT] Step 3: SUCCESS - Encrypted CEK in " + (encryptTime - findKeyTime) + " ms");
        System.out.println("[PGP_ENCRYPT] Wrapped CEK size: " + wrappedCek.length + " bytes");
        System.out.println("[PGP_ENCRYPT] COMPLETED - Total time: " + totalTime + " ms");

        return wrappedCek;
    }

    /**
     * Lưu DocumentKey vào database
     */
    private void saveDocumentKey(UUID versionId, String userId, byte[] wrappedCek) throws SQLException {
        System.out.println("[DB_SAVE] Starting to save DocumentKey - VersionId: " + versionId + ", UserId: " + userId);
        System.out.println("[DB_SAVE] Wrapped CEK size: " + wrappedCek.length + " bytes");

        long startTime = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            long connectTime = System.currentTimeMillis();
            System.out.println("[DB_SAVE] Connected to database in " + (connectTime - startTime) + " ms");

            String sql = "INSERT INTO document_keys (id, document_version_id, recipient_id, wrapped_cek, algorithm, created_at) " +
                    "VALUES (gen_random_uuid(), ?, ?, ?, 'OPENPGP_AES256', ?) " +
                    "ON CONFLICT (document_version_id, recipient_id) DO UPDATE SET wrapped_cek = EXCLUDED.wrapped_cek, algorithm = EXCLUDED.algorithm";
            System.out.println("[DB_SAVE] SQL: " + sql);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                Timestamp createdAt = new Timestamp(System.currentTimeMillis());
                stmt.setObject(1, versionId);
                stmt.setString(2, userId);
                stmt.setBytes(3, wrappedCek);
                stmt.setTimestamp(4, createdAt);
                System.out.println("[DB_SAVE] Parameters: versionId=" + versionId + ", userId=" + userId + ", createdAt=" + createdAt);

                long executeStartTime = System.currentTimeMillis();
                int rowsAffected = stmt.executeUpdate();
                long executeEndTime = System.currentTimeMillis();

                System.out.println("[DB_SAVE] SUCCESS - ExecuteUpdate completed in " + (executeEndTime - executeStartTime) + " ms");
                System.out.println("[DB_SAVE] Rows affected: " + rowsAffected);
            }

            long endTime = System.currentTimeMillis();
            System.out.println("[DB_SAVE] Total time for saveDocumentKey: " + (endTime - startTime) + " ms");
        } catch (SQLException e) {
            System.err.println("[DB_SAVE] ERROR - Failed to save DocumentKey: " + e.getMessage());
            System.err.println("[DB_SAVE] SQL State: " + e.getSQLState());
            System.err.println("[DB_SAVE] Error Code: " + e.getErrorCode());
            System.err.println("[DB_SAVE] VersionId: " + versionId);
            System.err.println("[DB_SAVE] UserId: " + userId);
            e.printStackTrace();
            throw e;
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

    /**
     * Exception đặc biệt cho trường hợp invalid ciphertext format
     * Exception này sẽ được catch riêng để skip version một cách graceful
     */
    private static class InvalidCiphertextFormatException extends Exception {
        InvalidCiphertextFormatException(String message) {
            super(message);
        }
    }
}
