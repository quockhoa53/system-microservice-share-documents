package com.system_share_documents.UserService.controller.user;

import com.system_share_documents.AppCommonService.enums.ActionLog;
import com.system_share_documents.AppCommonService.event.AuditLogEvent;
import com.system_share_documents.AppCommonService.kafka.producer.AuditLogProducer;
import com.system_share_documents.UserService.dto.ApiResponse;
import com.system_share_documents.UserService.dto.response.UserResponse;
import com.system_share_documents.UserService.service.AuthKeycloakUserService;

import com.system_share_documents.UserService.repository.UserRepository;
import com.system_share_documents.UserService.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.system_share_documents.AppCommonService.utils.ClientUtils.getClientIp;
import static com.system_share_documents.AppCommonService.utils.ClientUtils.getUserAgent;

@Slf4j
@RestController
@RequestMapping("/api/auth/")
public class AuthController {

    @Autowired
    private AuthKeycloakUserService authKeycloakUserService;

    @Autowired(required = false)
    private AuditLogProducer auditLogProducer;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("login")
    public ApiResponse<UserResponse> login(Authentication auth) {
        UserResponse userResponse = authKeycloakUserService.ensureUser(auth);
        return ApiResponse.success("OK", "Login successful", userResponse);
    }

    @PostMapping("logout")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) Map<String, String> requestBody,
            Authentication auth, 
            HttpServletRequest httpRequest) {
        log.info("[Logout] Logout endpoint called - auth: {}, httpRequest: {}", 
                auth != null ? "not null" : "null", 
                httpRequest != null ? "not null" : "null");
        
        String userId = null;
        try {
            // Kiểm tra auditLogProducer trước
            if (auditLogProducer == null) {
                log.warn("[Logout] AuditLogProducer is null! Cannot send audit log.");
            } else {
                log.debug("[Logout] AuditLogProducer is available");
            }
            
            // Ưu tiên lấy userId từ request body (đảm bảo có userId ngay cả khi token hết hạn)
            if (requestBody != null && requestBody.containsKey("userId") && requestBody.get("userId") != null) {
                userId = requestBody.get("userId");
                log.info("[Logout] Got userId from request body: {}", userId);
            }
            // Nếu không có trong request body, thử lấy từ JWT
            else if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                userId = jwt.getSubject();
                log.info("[Logout] Got userId from JWT: {}", userId);
            } 
            // Fallback: thử lấy từ SecurityUtils
            else if (auth != null) {
                try {
                    userId = SecurityUtils.requireCurrentUserId(auth, userRepository).toString();
                    log.info("[Logout] Got userId from SecurityUtils: {}", userId);
                } catch (Exception e) {
                    // Nếu không lấy được userId thì log warning
                    log.warn("[Logout] Cannot get userId from auth: {}", e.getMessage());
                }
            } else {
                log.warn("[Logout] Authentication is null and no userId in request body - cannot get userId");
            }

            // Gửi audit log nếu có userId và producer
            if (userId != null) {
                if (auditLogProducer != null) {
                    log.info("[Logout] Calling sendLogoutAuditLog - userId: {}", userId);
                    sendLogoutAuditLog(userId, httpRequest);
                } else {
                    log.error("[Logout] AuditLogProducer is null, cannot send audit log for userId: {}", userId);
                }
            } else {
                log.error("[Logout] userId is null, cannot send audit log. Auth: {}, Principal: {}", 
                        auth != null ? "not null" : "null",
                        auth != null && auth.getPrincipal() != null ? auth.getPrincipal().getClass().getName() : "null");
            }

            log.info("[Logout] Logout completed successfully");
            return ApiResponse.success("OK", "Logout successful", null);
        } catch (Exception e) {
            // Log lỗi nhưng vẫn return success để không ảnh hưởng đến logout flow
            log.error("[Logout] Error during logout: {}", e.getMessage(), e);
            return ApiResponse.success("OK", "Logout successful", null);
        }
    }

    private void sendLogoutAuditLog(String userId, HttpServletRequest httpRequest) {
        log.info("[Logout] sendLogoutAuditLog method called - userId: {}", userId);
        try {
            log.info("[AuditLog] Sending audit log - action: LOGOUT, userId: {}", userId);
            
            if (auditLogProducer == null) {
                log.error("[Logout] auditLogProducer is null in sendLogoutAuditLog method!");
                return;
            }
            
            String ip = httpRequest != null ? getClientIp(httpRequest) : "unknown";
            String userAgent = httpRequest != null ? getUserAgent(httpRequest) : "unknown";
            
            log.debug("[Logout] IP: {}, UserAgent: {}", ip, userAgent);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("action", ActionLog.LOGOUT.toString());
            metadata.put("timestamp", Instant.now().toString());

            AuditLogEvent logEvent = AuditLogEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(userId)
                    .action(String.valueOf(ActionLog.LOGOUT))
                    .objectType("user")
                    .typeLog("USER")
                    .status("OK")
                    .errorReason(null)
                    .ip(ip)
                    .userAgent(userAgent)
                    .metadata(convertMetadataToJson(metadata))
                    .timestamp(Instant.now())
                    .build();

            log.info("[Logout] Created AuditLogEvent - requestId: {}, userId: {}, action: {}", 
                    logEvent.getRequestId(), logEvent.getUserId(), logEvent.getAction());
            
            auditLogProducer.sendAuditLog(logEvent, userId);
            log.info("[AuditLog] Called sendAuditLog - action: LOGOUT, userId: {}", userId);
        } catch (Exception e) {
            log.error("[AuditLog] Failed to send audit log - action: LOGOUT, userId: {}, error: {}", userId, e.getMessage(), e);
            e.printStackTrace();
        }
    }

    private String convertMetadataToJson(Map<String, Object> metadata) {
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }
}