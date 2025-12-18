package com.system_share_documents.AppCommonService.service;

public interface EmailService {
    /**
     * Send email to recipient
     * @param to recipient email address
     * @param subject email subject
     * @param content email content (plain text)
     * @return true if email sent successfully, false otherwise
     */
    boolean sendEmail(String to, String subject, String content);

    /**
     * Send email to recipient with HTML content
     * @param to recipient email address
     * @param subject email subject
     * @param htmlContent email content (HTML)
     * @return true if email sent successfully, false otherwise
     */
    boolean sendHtmlEmail(String to, String subject, String htmlContent);
}

