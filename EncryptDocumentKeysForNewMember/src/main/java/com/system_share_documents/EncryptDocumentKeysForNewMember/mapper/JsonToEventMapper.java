package com.system_share_documents.EncryptDocumentKeysForNewMember.mapper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.system_share_documents.EncryptDocumentKeysForNewMember.entity.MemberJoinedGroupEvent;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;

import java.io.IOException;
import java.time.Instant;

/**
 * Map function để deserialize JSON string thành MemberJoinedGroupEvent
 */
public class JsonToEventMapper extends RichMapFunction<String, MemberJoinedGroupEvent> {

    private transient ObjectMapper objectMapper;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // Configure ObjectMapper to handle Java 8 time types
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Custom deserializer for Instant to handle numeric timestamps (seconds with fractional nanoseconds)
        SimpleModule instantModule = new SimpleModule("InstantModule");
        instantModule.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            @Override
            public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                if (p.getCurrentToken() == JsonToken.VALUE_NULL) {
                    return null;
                }
                // Handle numeric timestamp (seconds with fractional nanoseconds)
                if (p.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT || p.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
                    double seconds = p.getDoubleValue();
                    long epochSeconds = (long) seconds;
                    long nanoAdjustment = (long) ((seconds - epochSeconds) * 1_000_000_000);
                    return Instant.ofEpochSecond(epochSeconds, nanoAdjustment);
                }
                // Handle ISO-8601 string format
                if (p.getCurrentToken() == JsonToken.VALUE_STRING) {
                    return Instant.parse(p.getText());
                }
                throw new IOException("Cannot deserialize Instant from token: " + p.getCurrentToken());
            }
        });
        objectMapper.registerModule(instantModule);

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public MemberJoinedGroupEvent map(String json) throws Exception {
        try {
            if (json == null || json.trim().isEmpty()) {
                System.err.println("⚠️ Received empty JSON record from Kafka, skipping");
                return null;
            }
            MemberJoinedGroupEvent event = objectMapper.readValue(json, MemberJoinedGroupEvent.class);
            System.out.println("📥 Received MemberJoinedGroupEvent - groupId: " + event.getGroupId() +
                    ", userId: " + event.getUserId());
            return event;
        } catch (Exception e) {
            System.err.println("❌ Failed to parse JSON: " + json);
            e.printStackTrace();
            return null;
        }
    }
}

