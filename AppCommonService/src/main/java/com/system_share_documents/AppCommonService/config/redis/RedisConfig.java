package com.system_share_documents.AppCommonService.config.redis;

import io.redisearch.client.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

    @Bean
    public Client rediSearchClient() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(20);
        poolConfig.setMinIdle(5);

        JedisPool pool = new JedisPool(poolConfig, "localhost", 6379);
        return new Client("idx:documents", pool);
    }
}

