package com.system_share_documents.AppCommonService.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ProcessJsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String convertJson(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }
}
