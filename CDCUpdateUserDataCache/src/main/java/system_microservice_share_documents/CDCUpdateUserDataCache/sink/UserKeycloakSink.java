package system_microservice_share_documents.CDCUpdateUserDataCache.sink;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import system_microservice_share_documents.CDCUpdateUserDataCache.config.KeycloakConfig;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.util.*;

public class UserKeycloakSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(UserKeycloakSink.class);

    private transient Keycloak keycloak;
    private transient RealmResource realmResource;
    private transient UsersResource usersResource;
    private transient Counter recordsSinked;
    private transient String realm;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);

        KeycloakConfig keycloakConfig = new KeycloakConfig();
        keycloak = keycloakConfig.keycloakAdmin();
        realm = keycloakConfig.getRealm();
        realmResource = keycloak.realm(realm);
        usersResource = realmResource.users();

        recordsSinked = getRuntimeContext().getMetricGroup().counter("recordsSinkedKeycloak");

        log.info("Initialized Keycloak sink for realm: {}", realm);
    }

    @Override
    public void invoke(Map<String, Object> userData, Context context) throws Exception {
        if (userData == null) return;

        Object idObj = userData.get("id");
        if (idObj == null) {
            log.warn("Skipping record without 'id' field: {}", userData);
            return;
        }

        String userId = String.valueOf(idObj);
        String operation = (String) userData.get("_operation");
        if (operation == null) return;

        try {
            switch (operation) {
                case "CREATE":
                case "SNAPSHOT":
                    createOrUpdateUser(userId, userData, true);
                    break;

                case "UPDATE":
                    createOrUpdateUser(userId, userData, false);
                    break;

                case "DELETE":
                    deleteUser(userId);
                    break;

                default:
                    log.warn("Unknown operation: {} for user ID={}", operation, userId);
            }
        } catch (Exception e) {
            log.error("Error processing user {} with operation {}: {}", userId, operation, e.getMessage(), e);
        }
    }

    private void createOrUpdateUser(String userId, Map<String, Object> userData, boolean isCreate) {
        try {
            // Map database fields to Keycloak user representation
            UserRepresentation user = new UserRepresentation();

            // Set username (required in Keycloak)
            Object usernameObj = userData.get("username");
            String username = usernameObj != null ? String.valueOf(usernameObj) : null;
            if (username == null || username.isEmpty()) {
                // Fallback to email if username is not available
                Object emailObj = userData.get("email");
                username = emailObj != null ? String.valueOf(emailObj) : userId;
            }
            user.setUsername(username);

            // Set email
            Object emailObj = userData.get("email");
            if (emailObj != null) {
                user.setEmail(String.valueOf(emailObj));
                user.setEmailVerified(false); // Set based on your business logic
            }

            // Set first name and last name
            Object firstNameObj = userData.get("firstName");
            Object lastNameObj = userData.get("lastName");
            if (firstNameObj != null) {
                user.setFirstName(String.valueOf(firstNameObj));
            }
            if (lastNameObj != null) {
                user.setLastName(String.valueOf(lastNameObj));
            }

            // Set enabled status
            Object enabledObj = userData.get("enabled");
            if (enabledObj != null) {
                if (enabledObj instanceof Boolean) {
                    user.setEnabled((Boolean) enabledObj);
                } else {
                    user.setEnabled(Boolean.parseBoolean(String.valueOf(enabledObj)));
                }
            } else {
                user.setEnabled(true); // Default to enabled
            }

            // Set attributes (custom fields)
            Map<String, List<String>> attributes = new HashMap<>();

            // Add user ID as attribute
            attributes.put("db_user_id", Collections.singletonList(userId));

            // Add other custom fields from profile or userData
            Object profileObj = userData.get("profile");
            if (profileObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> profile = (Map<String, Object>) profileObj;
                for (Map.Entry<String, Object> entry : profile.entrySet()) {
                    if (entry.getValue() != null) {
                        attributes.put("profile." + entry.getKey(),
                                Collections.singletonList(String.valueOf(entry.getValue())));
                    }
                }
            }

            // Add phone number if available
            Object phoneObj = userData.get("phone");
            if (phoneObj != null) {
                attributes.put("phone", Collections.singletonList(String.valueOf(phoneObj)));
            }

            user.setAttributes(attributes);

            // Check if user exists
            List<UserRepresentation> existingUsers = usersResource.search(username, true);
            UserResource userResource = null;

            for (UserRepresentation existingUser : existingUsers) {
                if (existingUser.getUsername().equals(username) ||
                        (user.getEmail() != null && user.getEmail().equals(existingUser.getEmail()))) {
                    userResource = usersResource.get(existingUser.getId());
                    break;
                }
            }

            if (userResource != null) {
                // Update existing user
                UserRepresentation existingUser = userResource.toRepresentation();
                user.setId(existingUser.getId());

                // Merge attributes with existing ones
                Map<String, List<String>> mergedAttributes = new HashMap<>();
                if (existingUser.getAttributes() != null) {
                    mergedAttributes.putAll(existingUser.getAttributes());
                }
                mergedAttributes.putAll(attributes);
                user.setAttributes(mergedAttributes);

                userResource.update(user);
                recordsSinked.inc();
                log.info("Updated user in Keycloak: username={}, id={}", username, userId);
            } else if (isCreate) {
                // Create new user
                Response response = usersResource.create(user);
                if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                    recordsSinked.inc();
                    log.info("Created user in Keycloak: username={}, id={}", username, userId);
                } else if (response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
                    // User already exists, try to update instead
                    log.warn("User already exists in Keycloak, attempting update: username={}, id={}", username, userId);
                    List<UserRepresentation> u = usersResource.search(username, true);
                    if (!u.isEmpty()) {
                        UserResource existingUserResource = usersResource.get(u.get(0).getId());
                        UserRepresentation existingUser = existingUserResource.toRepresentation();
                        user.setId(existingUser.getId());

                        // Merge attributes
                        Map<String, List<String>> mergedAttributes = new HashMap<>();
                        if (existingUser.getAttributes() != null) {
                            mergedAttributes.putAll(existingUser.getAttributes());
                        }
                        mergedAttributes.putAll(attributes);
                        user.setAttributes(mergedAttributes);

                        existingUserResource.update(user);
                        recordsSinked.inc();
                        log.info("Updated existing user in Keycloak: username={}, id={}", username, userId);
                    }
                } else {
                    log.error("Failed to create user in Keycloak: username={}, status={}",
                            username, response.getStatus());
                }
                response.close();
            } else {
                log.warn("User not found for update: username={}, id={}", username, userId);
            }

        } catch (Exception e) {
            log.error("Error creating/updating user {} in Keycloak: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private void deleteUser(String userId) {
        try {
            // Find user by db_user_id attribute
            List<UserRepresentation> allUsers = usersResource.search(null, 0, Integer.MAX_VALUE);

            for (UserRepresentation user : allUsers) {
                Map<String, List<String>> attrs = user.getAttributes();
                if (attrs != null) {
                    List<String> dbUserIdList = attrs.get("db_user_id");
                    if (dbUserIdList != null && !dbUserIdList.isEmpty() &&
                            dbUserIdList.get(0).equals(userId)) {
                        usersResource.delete(user.getId());
                        recordsSinked.inc();
                        log.info("Deleted user from Keycloak: id={}, keycloakId={}", userId, user.getId());
                        return;
                    }
                }
            }

            log.warn("User not found in Keycloak for deletion: id={}", userId);
        } catch (NotFoundException e) {
            log.warn("User already deleted or not found in Keycloak: id={}", userId);
        } catch (Exception e) {
            log.error("Error deleting user {} from Keycloak: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void close() throws Exception {
        if (keycloak != null) {
            try {
                keycloak.close();
            } catch (Exception e) {
                log.warn("Error closing Keycloak connection: {}", e.getMessage());
            }
        }
        super.close();
    }
}

