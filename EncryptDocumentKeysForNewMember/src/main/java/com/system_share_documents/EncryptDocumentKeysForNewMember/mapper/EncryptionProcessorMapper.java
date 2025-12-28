package com.system_share_documents.EncryptDocumentKeysForNewMember.mapper;

import com.system_share_documents.EncryptDocumentKeysForNewMember.entity.MemberJoinedGroupEvent;
import com.system_share_documents.EncryptDocumentKeysForNewMember.service.DocumentKeyEncryptionService;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

import java.io.InputStream;
import java.util.Properties;

/**
 * Map function để xử lý mã hóa CEK cho member mới
 */
public class EncryptionProcessorMapper extends RichMapFunction<MemberJoinedGroupEvent, MemberJoinedGroupEvent> {

    private transient DocumentKeyEncryptionService encryptionService;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        try {
            Properties props = loadProperties("application.properties");
            encryptionService = new DocumentKeyEncryptionService(props);
        } catch (Exception e) {
            System.err.println("Failed to initialize EncryptionProcessorMapper: " + e.getMessage());
            throw new RuntimeException("Failed to initialize EncryptionProcessorMapper", e);
        }
    }

    @Override
    public MemberJoinedGroupEvent map(MemberJoinedGroupEvent event) throws Exception {
        if (event == null) {
            System.out.println("[ENCRYPTION_PROCESSOR] Received null event, skipping...");
            return null;
        }

        if (encryptionService == null) {
            System.err.println("[ENCRYPTION_PROCESSOR] ERROR - EncryptionService is not initialized");
            throw new RuntimeException("EncryptionService is not initialized");
        }

        String userId = event.getUserId();
        String groupId = event.getGroupId();
        String requestId = event.getRequestId();

        System.out.println("=================================================================");
        System.out.println("[ENCRYPTION_PROCESSOR] Starting encryption process");
        System.out.println("[ENCRYPTION_PROCESSOR] RequestId: " + requestId);
        System.out.println("[ENCRYPTION_PROCESSOR] GroupId: " + groupId);
        System.out.println("[ENCRYPTION_PROCESSOR] UserId: " + userId);
        System.out.println("[ENCRYPTION_PROCESSOR] Role: " + event.getRole());
        System.out.println("[ENCRYPTION_PROCESSOR] Timestamp: " + event.getTimestamp());
        System.out.println("=================================================================");

        try {
            if (userId == null || groupId == null) {
                System.out.println("[ENCRYPTION_PROCESSOR] WARNING - Missing required fields (userId or groupId is null), skipping event");
                return event; // Skip this event
            }

            long startTime = System.currentTimeMillis();
            encryptionService.encryptCEKForNewMember(event);
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            System.out.println("=================================================================");
            System.out.println("[ENCRYPTION_PROCESSOR] Completed encryption process successfully");
            System.out.println("[ENCRYPTION_PROCESSOR] RequestId: " + requestId);
            System.out.println("[ENCRYPTION_PROCESSOR] GroupId: " + groupId);
            System.out.println("[ENCRYPTION_PROCESSOR] UserId: " + userId);
            System.out.println("[ENCRYPTION_PROCESSOR] Processing time: " + duration + " ms");
            System.out.println("=================================================================");

            return event;
        } catch (Exception e) {
            System.err.println("=================================================================");
            System.err.println("[ENCRYPTION_PROCESSOR] ERROR - Failed to encrypt CEK for member");
            System.err.println("[ENCRYPTION_PROCESSOR] RequestId: " + requestId);
            System.err.println("[ENCRYPTION_PROCESSOR] UserId: " + userId);
            System.err.println("[ENCRYPTION_PROCESSOR] GroupId: " + groupId);
            System.err.println("[ENCRYPTION_PROCESSOR] Error message: " + e.getMessage());
            System.err.println("[ENCRYPTION_PROCESSOR] Error type: " + e.getClass().getName());
            e.printStackTrace();
            System.err.println("=================================================================");
            return event;
        }
    }

    private Properties loadProperties(String filename) throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (in == null) {
                throw new RuntimeException("File not found: " + filename);
            }
            props.load(in);
        }
        return props;
    }
}

