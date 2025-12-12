package com.system_share_documents.AppCommonService.listener;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Application Lifecycle Listener
 * Log khi service khởi động thành công và khi service bị tắt
 */
@Slf4j
@Component
public class ApplicationLifecycleListener implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ApplicationLifecycleListener(Environment environment) {
        this.environment = environment;
    }

    /**
     * Log khi application đã sẵn sàng (startup thành công)
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            String appName = environment.getProperty("spring.application.name", "unknown-service");
            String port = environment.getProperty("server.port", "unknown");
            String profile = String.join(",", environment.getActiveProfiles().length > 0
                    ? environment.getActiveProfiles()
                    : new String[]{"default"});

            String hostAddress = getHostAddress();
            String contextPath = environment.getProperty("server.servlet.context-path", "");
            String fullUrl = "http://" + hostAddress + ":" + port + contextPath;

            String startupTime = LocalDateTime.now().format(FORMATTER);

            // Đặt thông tin vào MDC để gửi lên Graylog
            MDC.put("eventType", "SERVICE_STARTUP");
            MDC.put("application", appName);
            MDC.put("port", port);
            MDC.put("profile", profile);
            MDC.put("host", hostAddress);
            MDC.put("startupTime", startupTime);

            log.info("[SERVICE] Application '{}' started successfully | Port: {} | Profile: {} | URL: {} | Time: {}",
                    appName, port, profile, fullUrl, startupTime);

            // Log thông tin thêm về environment
            log.info("[SERVICE] Environment details - Active Profiles: {} | Context Path: {} | Host: {}",
                    profile, contextPath.isEmpty() ? "/" : contextPath, hostAddress);

        } catch (Exception e) {
            log.error("[SERVICE] Error logging startup event", e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Log khi application bị tắt (shutdown)
     */
    @EventListener
    public void onApplicationClosed(ContextClosedEvent event) {
        try {
            String appName = environment.getProperty("spring.application.name", "unknown-service");
            String port = environment.getProperty("server.port", "unknown");
            String shutdownTime = LocalDateTime.now().format(FORMATTER);

            // Đặt thông tin vào MDC để gửi lên Graylog
            MDC.put("eventType", "SERVICE_SHUTDOWN");
            MDC.put("application", appName);
            MDC.put("port", port);
            MDC.put("shutdownTime", shutdownTime);

            log.warn("[SERVICE] Application '{}' is shutting down | Port: {} | Time: {}",
                    appName, port, shutdownTime);

        } catch (Exception e) {
            log.error("[SERVICE] Error logging shutdown event", e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Lấy địa chỉ IP của host
     */
    private String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            try {
                return InetAddress.getLoopbackAddress().getHostAddress();
            } catch (Exception ex) {
                return "unknown";
            }
        }
    }
}

