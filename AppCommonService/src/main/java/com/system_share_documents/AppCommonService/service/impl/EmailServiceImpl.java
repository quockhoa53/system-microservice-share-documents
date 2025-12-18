package com.system_share_documents.AppCommonService.service.impl;

import com.system_share_documents.AppCommonService.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public boolean sendEmail(String to, String subject, String content) {
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Email will not be sent.");
            log.info("Email would be sent to: {}, Subject: {}, Content: {}", to, subject, content);
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to: {}, Error: {}", to, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Email will not be sent.");
            log.info("HTML Email would be sent to: {}, Subject: {}, Content: {}", to, subject, htmlContent);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("HTML email sent successfully to: {}", to);
            return true;
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to: {}, Error: {}", to, e.getMessage(), e);
            return false;
        }
    }
}
