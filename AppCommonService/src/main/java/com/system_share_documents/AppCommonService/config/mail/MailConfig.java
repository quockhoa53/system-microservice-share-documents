package com.system_share_documents.AppCommonService.config.mail;

import com.system_share_documents.AppCommonService.config.properties.EmailProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
@ConditionalOnProperty(prefix = "mail", name = "host")
public class MailConfig {

    @Autowired
    private EmailProperties emailProperties;

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(emailProperties.getHost());
        mailSender.setPort(emailProperties.getPort());
        mailSender.setUsername(emailProperties.getUsername());
        mailSender.setPassword(emailProperties.getPassword());

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        if (emailProperties.getProperties() != null
                && emailProperties.getProperties().getMail() != null
                && emailProperties.getProperties().getMail().getSmtp() != null) {
            if (emailProperties.getProperties().getMail().getSmtp().getAuth() != null) {
                props.put("mail.smtp.auth", emailProperties.getProperties().getMail().getSmtp().getAuth());
            }
            if (emailProperties.getProperties().getMail().getSmtp().getStarttls() != null
                    && emailProperties.getProperties().getMail().getSmtp().getStarttls().getEnable() != null) {
                props.put("mail.smtp.starttls.enable", emailProperties.getProperties().getMail().getSmtp().getStarttls().getEnable());
            }
        }
        props.put("mail.debug", "false");

        return mailSender;
    }
}


