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
        // Load properties và khởi tạo encryption service
        Properties props = loadProperties("application.properties");
        encryptionService = new DocumentKeyEncryptionService(props);
    }

    @Override
    public MemberJoinedGroupEvent map(MemberJoinedGroupEvent event) throws Exception {
        try {
            System.out.println("🔐 Processing encryption for member " + event.getUserId() +
                    " in group " + event.getGroupId());
            encryptionService.encryptCEKForNewMember(event);
            System.out.println("✅ Successfully encrypted CEK for member " + event.getUserId() +
                    " in group " + event.getGroupId());
            return event;
        } catch (Exception e) {
            System.err.println("❌ Failed to encrypt CEK for member " + event.getUserId() +
                    " in group " + event.getGroupId() + ": " + e.getMessage());
            e.printStackTrace();
            // Không throw để không làm chết job, sẽ retry sau
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

