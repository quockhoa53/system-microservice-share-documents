package system_microservice_share_documents.CDCUpdateDocumentDataCache.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import system_microservice_share_documents.CDCUpdateDocumentDataCache.config.JobConfig;

import java.util.Map;
import java.util.stream.Collectors;

public class DocumentRedisSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(DocumentRedisSink.class);

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
    public void invoke(Map<String, Object> documentData, Context context) throws Exception {
        if (documentData == null) return;
        recordsSinked.inc();
        Object idObj = documentData.get("id");
        Object ownerIdObj = documentData.get("owner_id");
        if (idObj == null || ownerIdObj == null) {
            log.warn("Skipping record without 'id' or 'ownerId' field: {}", documentData);
            return;
        }

        String documentId = String.valueOf(idObj);
        String ownerId = String.valueOf(ownerIdObj);

        String redisKey = "document:" + documentId;
        String userSetKey = "user_documents:" + ownerId;

        String op = (String) documentData.get("_operation");
        if (op == null) return;

        try (Jedis jedis = jedisPool.getResource()) {
            if (redisUsername != null && !redisUsername.isEmpty() && redisPassword != null && !redisPassword.isEmpty()) {
                jedis.auth(redisUsername, redisPassword);
            }

            switch (op) {
                case "CREATE":
                case "UPDATE":
                case "SNAPSHOT":
                    documentData.remove("_operation");

                    // Chuyển tất cả value sang String, JSON hóa nếu cần
                    Map<String, String> hashData = documentData.entrySet().stream()
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
                    jedis.sadd(userSetKey, documentId);
                    log.info("{} document ID {} -> Redis HASH key={} + Set index={}", op, documentId, redisKey, userSetKey);
                    break;

                case "DELETE":
                    jedis.del(redisKey);
                    jedis.srem(userSetKey, documentId);
                    log.info("DELETE document ID {} -> removed Redis key={} and removed from Set={}", documentId, redisKey, userSetKey);
                    break;

                default:
                    log.warn("Unknown operation: {} for id={}", op, documentId);
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
