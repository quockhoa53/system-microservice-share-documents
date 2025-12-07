package com.system_share_documents.UserService.service.impl;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.entity.User;
import com.system_share_documents.UserService.exception.AppException;
import com.system_share_documents.UserService.exception.errorcode.AuthError;
import com.system_share_documents.UserService.exception.errorcode.SystemError;
import com.system_share_documents.UserService.mapper.UserMapper;
import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.service.AuthKeycloakUserService;
import com.system_share_documents.UserService.service.UserKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;
import static com.system_share_documents.UserService.constant.JwtClaims.*;
import static com.system_share_documents.UserService.constant.UserStatus.ACTIVE;
@Slf4j
@Service
public class AuthKeycloakUserServiceImpl implements AuthKeycloakUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserKeyService userKeyService;

    @Autowired(required = false)
    private AuditLogProducer auditLogProducer;

    @Override
    @Transactional
    public UserResponse ensureUser(Authentication auth) {
        try {
            if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
                throw new AppException(AuthError.UNAUTHORIZED);
            }

            String username = jwt.getClaimAsString(PREFERRED_USERNAME);
            String email = jwt.getClaimAsString(EMAIL);
            String fullName = jwt.getClaimAsString(NAME);
            String token = jwt.getTokenValue();

            if (username == null || username.isBlank()) {
                throw new AppException(AuthError.INVALID_JWT);
            }

            // Lấy userId từ Keycloak (sub)
            String sub = jwt.getSubject();
            UUID keycloakUserId;
            try {
                keycloakUserId = UUID.fromString(sub);
            } catch (IllegalArgumentException e) {
                // nếu sub không phải UUID hợp lệ
                throw new AppException(AuthError.INVALID_JWT, "Invalid Keycloak subject (not UUID)");
            }

            // Ưu tiên tìm theo id = Keycloak userId
            Optional<User> existingUser = userRepository.findById(keycloakUserId);

            User userEntity;
            boolean isNewUser = false;
            if (existingUser.isPresent()) {
                userEntity = existingUser.get();

                // (tuỳ chọn) sync lại thông tin nếu thay đổi trên Keycloak
                boolean changed = false;
                if (email != null && !email.equals(userEntity.getEmail())) {
                    userEntity.setEmail(email);
                    changed = true;
                }
                if (fullName != null && !fullName.equals(userEntity.getFullName())) {
                    userEntity.setFullName(fullName);
                    changed = true;
                }
                if (changed) {
                    userEntity.setUpdatedAt(Timestamp.from(Instant.now()));
                    userEntity = userRepository.save(userEntity);
                }
            } else {
                // Tạo user mới với id = Keycloak userId
                isNewUser = true;
                Timestamp now = Timestamp.from(Instant.now());
                userEntity = User.builder()
                        .id(keycloakUserId) // QUAN TRỌNG: id = sub
                        .username(username)
                        .email(email != null ? email : (sub + "@unknown.local"))
                        .fullName(fullName)
                        .status(ACTIVE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                userEntity = userRepository.save(userEntity);
            }

            UserResponse userResponse = userMapper.toResponse(userEntity);
            userResponse.setAccessToken(token);

            // Gửi audit log
            sendUserAuditLog(
                    isNewUser ? ActionLog.REGISTER : ActionLog.LOGIN,
                    keycloakUserId.toString(),
                    "OK",
                    null
            );

            return userResponse;

        } catch (AppException e) {
            // Gửi audit log cho login/register failed
            try {
                String userId = auth != null && auth.getPrincipal() instanceof Jwt jwt 
                    ? jwt.getSubject() 
                    : "unknown";
                sendUserAuditLog(
                        ActionLog.LOGIN,
                        userId,
                        "FAIL",
                        e.getMessage()
                );
            } catch (Exception logEx) {
                // Ignore audit log errors
            }
            throw e;
        } catch (Exception e) {
            throw new AppException(SystemError.INTERNAL_ERROR);
        }
    }

    /**
     * Helper method để gửi audit log cho user operations
     */
    private void sendUserAuditLog(ActionLog action, String userId, String status, String errorReason) {
        if (auditLogProducer == null) {
            log.warn("[AuditLog] AuditLogProducer is null, cannot send audit log for action: {}, userId: {}", action, userId);
            return; // Nếu không có producer thì bỏ qua
        }

        log.info("[AuditLog] Sending audit log - action: {}, userId: {}, status: {}", action, userId, status);
        try {
            HttpServletRequest httpRequest = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                httpRequest = attributes.getRequest();
            }

            String ip = httpRequest != null ? getClientIp(httpRequest) : "unknown";
            String userAgent = httpRequest != null ? getUserAgent(httpRequest) : "unknown";

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("action", action.toString());
            metadata.put("timestamp", Instant.now().toString());

            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(userId)
                    .action(String.valueOf(action))
                    .objectType("user")
                    .typeLog("USER")
                    .status("OK".equals(status) ? "OK" : "FAIL")
                    .errorReason(errorReason)
                    .ip(ip)
                    .userAgent(userAgent)
                    .metadata(convertMetadataToJson(metadata))
                    .timestamp(Instant.now())
                    .build();

            auditLogProducer.sendAuditLog(logEvent, userId);
            log.info("[AuditLog] Audit log sent successfully - action: {}, userId: {}", action, userId);
        } catch (Exception e) {
            log.error("[AuditLog] Failed to send audit log - action: {}, userId: {}, error: {}", action, userId, e.getMessage(), e);
            // Ignore audit log errors để không ảnh hưởng đến flow chính
        }
    }

    private String convertMetadataToJson(Map<String, Object> metadata) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }
}
