package com.system_share_documents.UserService.constant;

public final class JwtClaims {
    private JwtClaims() {}

    // Standard OpenID Connect claims
    public static final String SUB = "sub";
    public static final String PREFERRED_USERNAME = "preferred_username";
    public static final String EMAIL = "email";
    public static final String NAME = "name";

    // Keycloak-specific
    public static final String REALM_ACCESS = "realm_access";
    public static final String RESOURCE_ACCESS = "resource_access";
    public static final String ROLES = "roles";
    public static final String GROUPS = "groups";
}
