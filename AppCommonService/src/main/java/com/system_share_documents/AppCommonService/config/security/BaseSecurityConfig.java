package com.system_share_documents.AppCommonService.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import static com.system_share_documents.AppCommonService.constant.KeyCloakUrlConstant.GET_JWT_URL;

public abstract class BaseSecurityConfig {

    protected Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        return converter;
    }

    protected JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(GET_JWT_URL).build();
    }

    protected abstract void configureAuthorization(HttpSecurity http) throws Exception;

    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        configureAuthorization(http);
        return http.build();
    }
}
