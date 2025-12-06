package system_microservice_share_documents.CDCUpdatePublicKeyDataCache.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import system_microservice_share_documents.CDCUpdatePublicKeyDataCache.config.JobConfig;

import java.util.Map;

public class PublicKeyRedisSink extends RichSinkFunction<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(PublicKeyRedisSink.class);

    private transient JedisPool jedisPool;
    private transient ObjectMapper objectMapper;
    private transient String redisUsername;
    private transient String redisPassword;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        objectMapper = new ObjectMapper();

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

        Object idObj = documentData.get("id");
        if (idObj == null) {
            log.warn("Skipping record without 'id' field: {}", documentData);
            return;
        }
        String documentId = String.valueOf(idObj);
        String redisKey = "document:" + documentId;

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
                    String documentJson = objectMapper.writeValueAsString(documentData);
                    jedis.set(redisKey, documentJson);
                    log.info("{} document ID {} -> Redis key={}", op, documentId, redisKey);
                    break;

                case "DELETE":
                    jedis.del(redisKey);
                    log.info("DELETE document ID {} -> removed Redis key={}", documentId, redisKey);
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
