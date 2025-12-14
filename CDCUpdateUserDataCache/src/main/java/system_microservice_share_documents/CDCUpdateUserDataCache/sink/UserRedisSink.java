package system_microservice_share_documents.CDCUpdateUserDataCache.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import system_microservice_share_documents.CDCUpdateUserDataCache.config.JobConfig;

import java.util.Map;
import java.util.stream.Collectors;

public class UserRedisSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(UserRedisSink.class);

    private transient JedisPool jedisPool;
    private transient ObjectMapper objectMapper;
    private transient String redisUsername;
    private transient String redisPassword;
    private transient Counter recordsSinked;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        objectMapper = new ObjectMapper();
        recordsSinked = getRuntimeContext().getMetricGroup().counter("recordsSinked");
        String host = JobConfig.get("redis.host");
        int port = JobConfig.getInt("redis.port");
        redisUsername = JobConfig.get("redis.username");
        redisPassword = JobConfig.get("redis.password");
        int maxTotal = Math.max(1, JobConfig.getInt("redis.pool.maxTotal"));
        int maxIdle = Math.max(1, JobConfig.getInt("redis.pool.maxIdle"));

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

        // Note: JedisPool constructor only accepts password, not username
        // Username will be handled in invoke() via jedis.auth(username, password)
        if (redisPassword != null && !redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, redisPassword);
            log.info("Initialized Redis pool to {}:{} with password authentication", host, port);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port);
            log.info("Initialized Redis pool to {}:{} without password", host, port);
        }
        
        // Test connection
        try (Jedis testJedis = jedisPool.getResource()) {
            if (redisPassword != null && !redisPassword.isEmpty()) {
                if (redisUsername != null && !redisUsername.isEmpty()) {
                    testJedis.auth(redisUsername, redisPassword);
                } else {
                    testJedis.auth(redisPassword);
                }
            }
            testJedis.ping();
            log.info("Redis connection test successful");
        } catch (Exception e) {
            log.error("Failed to connect to Redis at {}:{} - {}", host, port, e.getMessage(), e);
            throw new RuntimeException("Cannot connect to Redis", e);
        }
    }

    @Override
    public void invoke(Map<String, Object> userData, Context context) throws Exception {
        if (userData == null) return;
        
        Object idObj = userData.get("id");
        if (idObj == null) {
            log.warn("Skipping record without 'id' field: {}", userData);
            return;
        }

        String userId = String.valueOf(idObj);
        String redisKey = "user:" + userId;

        String op = (String) userData.get("_operation");
        if (op == null) {
            log.warn("Skipping record without '_operation' field for id={}", userId);
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            // Redis authentication - handle both username+password and password-only cases
            if (redisPassword != null && !redisPassword.isEmpty()) {
                if (redisUsername != null && !redisUsername.isEmpty()) {
                    // Use username + password authentication
                    jedis.auth(redisUsername, redisPassword);
                } else {
                    // Use password-only authentication (legacy mode)
                    jedis.auth(redisPassword);
                }
            }

            switch (op) {
                case "CREATE":
                case "UPDATE":
                case "SNAPSHOT":
                    userData.remove("_operation");

                    // Chuyển tất cả value sang String, JSON hóa nếu cần
                    Map<String, String> hashData = userData.entrySet().stream()
                            .filter(e -> e.getValue() != null)
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> {
                                        Object val = e.getValue();
                                        try {
                                            if (val instanceof Map || val instanceof Iterable) {
                                                return objectMapper.writeValueAsString(val);
                                            }
                                        } catch (Exception ex) {
                                            log.warn("Failed to serialize field {}: {}", e.getKey(), ex.getMessage());
                                        }
                                        return String.valueOf(val);
                                    }
                            ));

                    jedis.hset(redisKey, hashData);
                    recordsSinked.inc();
                    log.info("{} user ID {} -> Redis HASH key={}", op, userId, redisKey);
                    break;

                case "DELETE":
                    jedis.del(redisKey);
                    recordsSinked.inc();
                    log.info("DELETE user ID {} -> removed Redis key={}", userId, redisKey);
                    break;

                default:
                    log.warn("Unknown operation: {} for id={}", op, userId);
            }
        } catch (Exception e) {
            log.error("Failed to write to Redis for user ID {} (operation: {}): {}", 
                    userId, op, e.getMessage(), e);
            // Re-throw to let Flink handle retry/backpressure
            throw new RuntimeException("Redis sink error for user " + userId, e);
        }
    }

    @Override
    public void close() throws Exception {
        if (jedisPool != null) {
            try {
                jedisPool.close();
            } catch (Exception ignored) { }
        }
        super.close();
    }
}
