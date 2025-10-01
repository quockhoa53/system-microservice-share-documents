package com.system_share_documents.DocumentService.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SerializeUtils {

    @Autowired
    private ObjectMapper objectMapper;

    public String serializeMetadata(Object metadata) {
        try {
            if (metadata == null) return "{}";
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }
}
