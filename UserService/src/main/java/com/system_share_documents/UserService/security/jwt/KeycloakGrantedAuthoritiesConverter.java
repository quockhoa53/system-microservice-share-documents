package com.system_share_documents.UserService.security.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;

import static com.system_share_documents.UserService.constant.JwtClaims.*;

public class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String resourceClientId;

    public KeycloakGrantedAuthoritiesConverter(String resourceClientId) {
        this.resourceClientId = resourceClientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();

        // 1) realm_access.roles
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS);
        if (realmAccess != null) {
            Object rs = realmAccess.get(ROLES);
            if (rs instanceof Collection<?> c) {
                c.forEach(r -> roles.add(String.valueOf(r)));
            }
        }

        // 2) resource_access.<clientId>.roles
        Map<String, Object> resourceAccess = jwt.getClaim(RESOURCE_ACCESS);
        if (resourceAccess != null && resourceAccess.get(resourceClientId) instanceof Map<?, ?> client) {
            Object cr = ((Map<?, ?>) client).get(ROLES);
            if (cr instanceof Collection<?> c) c.forEach(r -> roles.add(String.valueOf(r)));
        }

        // 3) groups (nếu có)
        Collection<String> groups = jwt.getClaimAsStringList(GROUPS);
        if (groups != null) {
            roles.addAll(
                    groups.stream()
                            .map(g -> "GROUP_" + g.replace("/", "")) // "/team-ai" -> "GROUP_team-ai"
                            .collect(Collectors.toSet())
            );
        }

        // chuẩn hóa thành ROLE_*
        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(r -> r.toUpperCase(Locale.ROOT)) // CHUẨN HOÁ HOA
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

    }
}