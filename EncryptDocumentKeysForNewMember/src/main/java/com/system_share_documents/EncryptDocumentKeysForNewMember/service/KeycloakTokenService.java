package com.system_share_documents.EncryptDocumentKeysForNewMember.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

/**
 * Service để gen token từ Keycloak sử dụng client_credentials grant type
 */
public class KeycloakTokenService {

    private final String tokenBaseUrl;
    private final String clientId;
    private final String clientSecret;
    private final int httpConnectTimeout;
    private final int httpSocketTimeout;
    private final ObjectMapper objectMapper;

    private String cachedToken;
    private Instant expiryTime = Instant.EPOCH;

    public KeycloakTokenService(Properties props) {
        // Support both environment variables and properties file
        // Priority: env variable > properties file
        String keycloakUrl = getProperty(props, "keycloak.url", "KEYCLOAK_URL");
        String keycloakRealm = getProperty(props, "keycloak.realm", "KEYCLOAK_REALM");

        // Build token URL: {keycloakUrl}/realms/{realm}/protocol/openid-connect/token
        if (keycloakUrl != null && keycloakRealm != null) {
            String baseUrl = keycloakUrl.endsWith("/") ? keycloakUrl.substring(0, keycloakUrl.length() - 1) : keycloakUrl;
            this.tokenBaseUrl = baseUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";
        } else {
            // Fallback: use full URL if provided
            this.tokenBaseUrl = getProperty(props, "keycloak.token.base.url", "KEYCLOAK_TOKEN_BASE_URL");
            if (this.tokenBaseUrl == null) {
                throw new RuntimeException("Keycloak configuration missing. Please provide either (keycloak.url + keycloak.realm) or keycloak.token.base.url (or env vars: KEYCLOAK_URL + KEYCLOAK_REALM)");
            }
        }

        // Support KEYCLOAK_TOKEN_CLIENT_ID and KEYCLOAK_TOKEN_CLIENT_SECRET env variables
        // (matching the working KeycloakTokenRestImpl configuration)
        this.clientId = getProperty(props, "keycloak.client.id", "KEYCLOAK_TOKEN_CLIENT_ID");
        this.clientSecret = getProperty(props, "keycloak.client.secret", "KEYCLOAK_TOKEN_CLIENT_SECRET");

        if (this.clientId == null || this.clientSecret == null) {
            throw new RuntimeException("Keycloak client.id and client.secret are required");
        }

        // LƯU Ý: UserService yêu cầu token có resource_access cho "frontend-app"
        // Để token từ "token-service" có resource_access cho "frontend-app", cần cấu hình trong Keycloak:
        // 1. Vào Keycloak Admin Console → Clients → token-service → Client scopes
        // 2. Thêm "frontend-app" vào Assigned client scopes (Default hoặc Optional)
        // 3. Hoặc tạo một protocol mapper để thêm resource_access cho frontend-app vào token

        // HTTP timeout configuration
        this.httpConnectTimeout = Integer.parseInt(props.getProperty("http.connect.timeout", "5000"));
        this.httpSocketTimeout = Integer.parseInt(props.getProperty("http.socket.timeout", "10000"));

        // Initialize ObjectMapper for JSON parsing
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Lấy access token từ Keycloak với caching và auto-refresh
     * Sử dụng client_credentials grant type
     */
    public synchronized String getAccessToken() {
        System.out.println("[KEYCLOAK_TOKEN] Getting access token from Keycloak");
        System.out.println("[KEYCLOAK_TOKEN] Token URL: " + tokenBaseUrl);

        // Kiểm tra token cache còn valid không
        if (cachedToken != null && Instant.now().isBefore(expiryTime)) {
            long secondsUntilExpiry = expiryTime.getEpochSecond() - Instant.now().getEpochSecond();
            System.out.println("[KEYCLOAK_TOKEN] Using cached token (valid for " + secondsUntilExpiry + " more seconds)");
            return cachedToken;
        }

        System.out.println("[KEYCLOAK_TOKEN] Token expired or not cached, requesting new token...");
        long startTime = System.currentTimeMillis();

        // Token hết hạn hoặc chưa có, gen token mới
        try (CloseableHttpClient httpClient = createHttpClient()) {
            HttpPost post = new HttpPost(tokenBaseUrl);
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");

            // Build form data: grant_type=client_credentials&client_id=...&client_secret=...
            // Note: Keycloak không hỗ trợ audience parameter trong token request
            // Token sẽ có resource_access cho chính client đó (token-service)
            // Để có resource_access cho frontend-app, cần cấu hình trong Keycloak:
            // - Thêm client scope cho frontend-app vào token-service client
            // - Hoặc dùng protocol mapper để thêm resource_access
            String formData = "grant_type=client_credentials" +
                    "&client_id=" + java.net.URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                    "&client_secret=" + java.net.URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);

            System.out.println("[KEYCLOAK_TOKEN] Client ID: " + clientId);
            System.out.println("[KEYCLOAK_TOKEN] Request body length: " + formData.length() + " chars");
            post.setEntity(new StringEntity(formData, StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                long responseTime = System.currentTimeMillis();
                int statusCode = response.getStatusLine().getStatusCode();
                System.out.println("[KEYCLOAK_TOKEN] Response received in " + (responseTime - startTime) + " ms");
                System.out.println("[KEYCLOAK_TOKEN] Status code: " + statusCode);

                if (statusCode != 200) {
                    String errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    System.err.println("[KEYCLOAK_TOKEN] ERROR - Status code: " + statusCode);
                    System.err.println("[KEYCLOAK_TOKEN] Error body: " + errorBody);
                    throw new RuntimeException("Failed to get token from Keycloak: " + statusCode + " - " + errorBody);
                }

                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                System.out.println("[KEYCLOAK_TOKEN] Response body length: " + responseBody.length() + " chars");

                // Parse JSON response using Jackson ObjectMapper - similar to RestTemplate approach
                // Response format: {"access_token":"...","expires_in":300,"token_type":"Bearer"}
                try {
                    // Parse JSON to Map - same approach as KeycloakTokenRestImpl
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bodyMap = objectMapper.readValue(responseBody, Map.class);

                    if (bodyMap == null || bodyMap.get("access_token") == null) {
                        System.err.println("[KEYCLOAK_TOKEN] ERROR - Invalid response: missing access_token");
                        System.err.println("[KEYCLOAK_TOKEN] Response body: " + responseBody);
                        throw new RuntimeException("Invalid token response from Keycloak: missing access_token. Response: " + responseBody);
                    }

                    cachedToken = (String) bodyMap.get("access_token");

                    // Validate and trim token
                    if (cachedToken == null || cachedToken.trim().isEmpty()) {
                        System.err.println("[KEYCLOAK_TOKEN] ERROR - Token is null or empty");
                        throw new RuntimeException("Token is null or empty from Keycloak response");
                    }
                    cachedToken = cachedToken.trim();

                    // Validate token format (JWT should have 3 parts separated by dots)
                    String[] tokenParts = cachedToken.split("\\.");
                    if (tokenParts.length != 3) {
                        System.err.println("[KEYCLOAK_TOKEN] WARNING - Token format may be invalid. Expected JWT format (3 parts), got " + tokenParts.length + " parts. Token preview: " +
                                (cachedToken.length() > 50 ? cachedToken.substring(0, 50) + "..." : cachedToken));
                        // Still proceed, but log warning
                    } else {
                        System.out.println("[KEYCLOAK_TOKEN] Token format validated (JWT with 3 parts)");
                    }

                    // Parse expires_in - same approach as KeycloakTokenRestImpl
                    Object exp = bodyMap.get("expires_in");
                    long expiresIn = 300; // Default 5 minutes
                    if (exp instanceof Number) {
                        expiresIn = ((Number) exp).longValue();
                    } else if (exp != null) {
                        try {
                            expiresIn = Long.parseLong(exp.toString());
                        } catch (NumberFormatException e) {
                            System.out.println("[KEYCLOAK_TOKEN] WARNING - Could not parse expires_in, using default 300 seconds");
                            // Use default 300 seconds
                        }
                    }

                    // Set expiry time (refresh 30 seconds before actual expiry)
                    expiryTime = Instant.now().plusSeconds(Math.max(30, expiresIn - 30));
                    long totalTime = System.currentTimeMillis() - startTime;

                    System.out.println("[KEYCLOAK_TOKEN] SUCCESS - Obtained token in " + totalTime + " ms");
                    System.out.println("[KEYCLOAK_TOKEN] Token length: " + cachedToken.length() + " chars");
                    System.out.println("[KEYCLOAK_TOKEN] Token preview: " +
                            (cachedToken.length() > 50 ? cachedToken.substring(0, 50) + "..." : cachedToken));
                    System.out.println("[KEYCLOAK_TOKEN] Expires in: " + expiresIn + " seconds");

                    return cachedToken;
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    System.err.println("[KEYCLOAK_TOKEN] ERROR - Failed to parse JSON response");
                    System.err.println("[KEYCLOAK_TOKEN] Response body: " + responseBody);
                    e.printStackTrace();
                    throw new RuntimeException("Failed to parse token response from Keycloak. Response body: " + responseBody, e);
                }
            }
        } catch (java.net.ConnectException | java.net.SocketTimeoutException e) {
            System.err.println("[KEYCLOAK_TOKEN] ERROR - Connection/Timeout error");
            System.err.println("[KEYCLOAK_TOKEN] URL: " + tokenBaseUrl);
            System.err.println("[KEYCLOAK_TOKEN] Error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to connect to Keycloak at " + tokenBaseUrl +
                    ". Please ensure Keycloak is running.", e);
        } catch (Exception e) {
            System.err.println("[KEYCLOAK_TOKEN] ERROR - Unexpected error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Unexpected error while getting token from Keycloak", e);
        }
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
     * Invalidate cached token (force refresh on next call)
     */
    public synchronized void invalidateToken() {
        System.out.println("[KEYCLOAK_TOKEN] Invalidating cached token");
        cachedToken = null;
        expiryTime = Instant.EPOCH;
        System.out.println("[KEYCLOAK_TOKEN] Token invalidated, next call will request new token");
    }

    /**
     * Get property from properties file or environment variable
     * Priority: environment variable > properties file
     */
    private String getProperty(Properties props, String propKey, String envKey) {
        // Check environment variable first
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }

        // Fallback to properties file
        String propValue = props.getProperty(propKey);
        if (propValue != null && !propValue.trim().isEmpty()) {
            return propValue.trim();
        }

        return null;
    }
}

