package com.system_share_documents.AppCommonService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "mail")
public class EmailProperties {
    private String host;
    private Integer port;
    private String username;
    private String password;
    private SmtpProperties properties = new SmtpProperties();

    @Data
    public static class SmtpProperties {
        private MailProperties mail = new MailProperties();

        @Data
        public static class MailProperties {
            private SmtpConfig smtp = new SmtpConfig();

            @Data
            public static class SmtpConfig {
                private Boolean auth;
                private StartTlsProperties starttls = new StartTlsProperties();

                @Data
                public static class StartTlsProperties {
                    private Boolean enable;
                }
            }
        }
    }
}