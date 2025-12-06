package system_microservice_share_documents.CDCUpdatePublicKeyDataCache.config;

import java.io.InputStream;
import java.util.Properties;

public class JobConfig {
    private static final Properties prop = new Properties();
    private static final String configFile = "application.properties";

    static {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(configFile)) {
            if (is == null) {
                throw new RuntimeException("Configuration file '" + configFile + "' not found");
            }
            prop.load(is);
        } catch (Exception e) {
            throw new RuntimeException("Error loading configuration file '" + configFile + "'", e);
        }
    }

    public static String get(String key) {
        return prop.getProperty(key);
    }

    public static int getInt(String key) {
        String v = prop.getProperty(key);
        if (v == null || v.isEmpty()) return 0;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return 0; }
    }

    public static long getLong(String key) {
        String v = prop.getProperty(key);
        if (v == null || v.isEmpty()) return 0L;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return 0L; }
    }
}
