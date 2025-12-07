package com.system_share_documents.UserService.config;


import com.system_share_documents.AppCommonService.config.security.BaseSecurityConfig;
import com.system_share_documents.UserService.security.jwt.KeycloakGrantedAuthoritiesConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends BaseSecurityConfig {

    /**
     * Khai báo rule phân quyền URL cho UserService
     */
    @Override
    protected void configureAuthorization(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // mở cho health + public
                        .requestMatchers("/actuator/health", "/public/**").permitAll()

                        // login: bắt buộc đã auth (có JWT)
                        .requestMatchers("/api/auth/login").authenticated()

                        // admin: cần ROLE_ADMIN (Keycloak realm/client role ADMIN)
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        //debug
                        .requestMatchers("/debug/**").authenticated()

                        // các API còn lại: cần đăng nhập
                        .anyRequest().authenticated()
                );
    }

    /**
     * Map role từ token Keycloak → GrantedAuthority (ROLE_xxx)
     * dùng KeycloakGrantedAuthoritiesConverter("frontend-app")
     */
    @Override
    protected Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(
                new KeycloakGrantedAuthoritiesConverter("frontend-app")
        );
        return converter;
    }

    /**
     * Bean SecurityFilterChain thực tế, dùng chung logic từ BaseSecurityConfig
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return securityFilterChain(http);
    }
}
