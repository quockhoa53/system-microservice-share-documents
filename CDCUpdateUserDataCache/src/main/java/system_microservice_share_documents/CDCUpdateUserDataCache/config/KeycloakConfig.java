package system_microservice_share_documents.CDCUpdateUserDataCache.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeycloakConfig {

    private static final Logger log = LoggerFactory.getLogger(KeycloakConfig.class);
    private static final String SERVER_URL = JobConfig.get("keycloak.server-url");
    private static final String ADMIN_REALM = JobConfig.get("keycloak.admin-realm");
    private static final String ADMIN_USERNAME = JobConfig.get("keycloak.admin-username");
    private static final String ADMIN_PASSWORD = JobConfig.get("keycloak.admin-password");
    private static final String CLIENT_ID = JobConfig.get("keycloak.client-id");
    private static final String REALM = JobConfig.get("keycloak.realm");

    public Keycloak keycloakAdmin() {
        try {
            Keycloak keycloak = KeycloakBuilder.builder()
                    .serverUrl(SERVER_URL)
                    .realm(ADMIN_REALM)
                    .username(ADMIN_USERNAME)
                    .password(ADMIN_PASSWORD)
                    .clientId(CLIENT_ID)
                    .build();

            // Verify connection by getting realm info
            keycloak.realm(REALM).toRepresentation();
            log.info("Connected to Keycloak successfully. Server: {}, Realm: {}", SERVER_URL, REALM);

            return keycloak;
        } catch (Exception e) {
            log.error("Failed to connect to Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Keycloak connection failed", e);
        }
    }

    public String getRealm() {
        return REALM;
    }
}

