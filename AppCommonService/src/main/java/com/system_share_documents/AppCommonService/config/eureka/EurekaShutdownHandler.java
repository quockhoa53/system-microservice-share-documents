package com.system_share_documents.AppCommonService.config.eureka;

import com.netflix.discovery.EurekaClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Eureka Shutdown Handler
 * Xử lý graceful shutdown cho Eureka Client để tránh lỗi khi de-register
 */
@Slf4j
@Component
public class EurekaShutdownHandler implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;
    private EurekaClient eurekaClient;

    @Autowired(required = false)
    public void setEurekaClient(EurekaClient eurekaClient) {
        this.eurekaClient = eurekaClient;
    }

    public EurekaShutdownHandler(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // No action needed on startup
    }

    /**
     * Xử lý graceful shutdown cho Eureka
     * De-register từ Eureka với timeout để tránh block shutdown
     */
    @EventListener
    public void onApplicationClosed(ContextClosedEvent event) {
        if (eurekaClient == null) {
            return; // Eureka không được cấu hình
        }

        String appName = environment.getProperty("spring.application.name", "unknown-service");

        try {
            log.info("[EUREKA] Attempting to de-register from Eureka server...");

            // Thực hiện de-register trong một thread riêng với timeout
            CompletableFuture<Void> deregisterFuture = CompletableFuture.runAsync(() -> {
                try {
                    eurekaClient.shutdown();
                    log.info("[EUREKA] Successfully de-registered '{}' from Eureka", appName);
                } catch (Exception e) {
                    // Log nhẹ nhàng, không throw exception
                    log.debug("[EUREKA] De-registration completed with minor issues (this is normal if Eureka server is unavailable): {}",
                            e.getMessage());
                }
            });

            // Đợi tối đa 5 giây cho de-registration
            try {
                deregisterFuture.get(5, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.warn("[EUREKA] De-registration timeout after 5 seconds, continuing shutdown...");
                deregisterFuture.cancel(true);
            } catch (Exception e) {
                // Bỏ qua các exception khác, không block shutdown
                log.debug("[EUREKA] De-registration encountered an issue (this is normal): {}", e.getMessage());
            }

        } catch (Exception e) {
            // Không throw exception để không block shutdown process
            log.debug("[EUREKA] Could not de-register from Eureka (this is normal if Eureka server is unavailable): {}",
                    e.getMessage());
        }
    }
}

