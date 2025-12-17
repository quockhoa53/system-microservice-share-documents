package com.system_share_documents.UserService.scheduler;

import com.system_share_documents.UserService.service.KeycloakGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakGroupSyncScheduler {

    private final KeycloakGroupService keycloakGroupService;

    // Đồng bộ mỗi 1 giờ (3600000 milliseconds)
    @Scheduled(fixedRate = 3600000)
    public void syncGroupsFromKeycloak() {
        log.info("Starting scheduled sync of Keycloak groups...");
        try {
            keycloakGroupService.syncAllGroupsFromKeycloak();
            log.info("Completed scheduled sync of Keycloak groups");
        } catch (Exception e) {
            log.error("Error during scheduled sync of Keycloak groups: {}", e.getMessage(), e);
        }
    }
}

















