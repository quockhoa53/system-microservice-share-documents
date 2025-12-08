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

        if (redisPassword != null && !redisPassword.isEmpty()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, 2000, redisPassword);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port);
        }

        log.info("Initialized Redis pool to {}:{}", host, port);
    }

    @Override
    public void invoke(Map<String, Object> userData, Context context) throws Exception {
        if (userData == null) return;
        recordsSinked.inc();
        Object idObj = userData.get("id");
        if (idObj == null) {
            log.warn("Skipping record without 'id' field: {}", userData);
            return;
        }

        String userId = String.valueOf(idObj);
        String redisKey = "user:" + userId;

        String op = (String) userData.get("_operation");
        if (op == null) return;

        try (Jedis jedis = jedisPool.getResource()) {
            if (redisUsername != null && !redisUsername.isEmpty() && redisPassword != null && !redisPassword.isEmpty()) {
                jedis.auth(redisUsername, redisPassword);
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
                    log.info("{} user ID {} -> Redis HASH key={}", op, userId, redisKey);
                    break;

                case "DELETE":
                    jedis.del(redisKey);
                    log.info("DELETE user ID {} -> removed Redis key={}", userId, redisKey);
                    break;

                default:
                    log.warn("Unknown operation: {} for id={}", op, userId);
            }
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
