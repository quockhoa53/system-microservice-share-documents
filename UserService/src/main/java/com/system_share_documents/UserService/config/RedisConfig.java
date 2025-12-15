package com.system_share_documents.UserService.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        // Custom module for java.sql.Timestamp - serialize as long (milliseconds)
        SimpleModule timestampModule = new SimpleModule("TimestampModule");
        timestampModule.addSerializer(java.sql.Timestamp.class, new com.fasterxml.jackson.databind.JsonSerializer<java.sql.Timestamp>() {
            @Override
            public void serialize(java.sql.Timestamp value, com.fasterxml.jackson.core.JsonGenerator gen, com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    gen.writeNumber(value.getTime());
                }
            }
        });
        timestampModule.addDeserializer(java.sql.Timestamp.class, new com.fasterxml.jackson.databind.JsonDeserializer<java.sql.Timestamp>() {
            @Override
            public java.sql.Timestamp deserialize(com.fasterxml.jackson.core.JsonParser p, com.fasterxml.jackson.databind.DeserializationContext ctxt) throws java.io.IOException {
                if (p.getCurrentToken() == com.fasterxml.jackson.core.JsonToken.VALUE_NULL) {
                    return null;
                }
                return new java.sql.Timestamp(p.getLongValue());
            }
        });
        mapper.registerModule(timestampModule);
        
        // Configure for Redis serialization
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
        mapper.configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        
        // Don't use default typing - it causes issues with java.sql.Timestamp
        // Instead, we'll handle type information manually in the serializer
        // GenericJackson2JsonRedisSerializer will add type info automatically for collections
        
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values with custom ObjectMapper
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Don't use default typing - it causes issues with java.sql.Timestamp
        // Instead, we'll serialize/deserialize manually with proper type handling
        // Use GenericJackson2JsonRedisSerializer without default typing
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Default TTL: 1 hour
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration("users", RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("groups", RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("groupMembers", RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration("userGroups", RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofMinutes(30)))
                .build();
    }
}


