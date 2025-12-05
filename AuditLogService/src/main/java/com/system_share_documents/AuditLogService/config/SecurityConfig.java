package com.system_share_documents.AuditLogService.config;

import com.system_share_documents.AppCommonService.config.security.BaseSecurityConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends BaseSecurityConfig {

    @Override
    protected void configureAuthorization(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                // Cho health/info nếu bạn dùng Actuator
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Endpoint internal cho các microservice khác (gateway, document-service...) gọi
                .requestMatchers("/internal/audit/**").permitAll()

                // Các API public bên ngoài (nếu có) thì yêu cầu JWT
                .requestMatchers("/api/audit/**").authenticated()

                // Còn lại chặn
                .anyRequest().denyAll()
        );
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // BaseSecurityConfig sẽ cấu hình chung (csrf, stateless, resource server...)
        return super.securityFilterChain(http);
    }

    @Bean
    @Override
    public JwtDecoder jwtDecoder() {
        return super.jwtDecoder();
    }
}
