package com.bayflow.auth.mail;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.bayflow.auth.config.AuthProperties;

/**
 * SMTP through Jakarta Mail, configured from {@code bayflow.auth.mail}: Gmail (smtp.gmail.com,
 * port 587, an App Password), Amazon SES, SendGrid, Mailgun or Mailpit locally; only settings
 * change between them. Deliberately not a Spring JavaMailSender bean, so the actuator does not
 * check the mail server on every health probe.
 */
@Component
public class SmtpMailGateway implements MailGateway {

    private static final String TIMEOUT_MS = "15000";

    private final AuthProperties.Mail settings;
    private final JavaMailSenderImpl sender;

    public SmtpMailGateway(AuthProperties properties) {
        this.settings = properties.getMail();
        this.sender = StringUtils.hasText(settings.getHost()) ? sender(settings) : null;
    }

    @Override
    public boolean isAvailable() {
        return sender != null && StringUtils.hasText(settings.getFrom());
    }

    @Override
    public void send(OutgoingMail mail) {
        if (!isAvailable()) {
            throw new IllegalStateException("E-mail is not set up (bayflow.auth.mail.host and .from)");
        }
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(settings.getFrom(), mail.senderName(), "UTF-8"));
            helper.setTo(mail.to());
            if (StringUtils.hasText(mail.replyTo())) {
                helper.setReplyTo(mail.replyTo());
            }
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Could not build the e-mail: " + e.getMessage(), e);
        }
        sender.send(message);
    }

    private static JavaMailSenderImpl sender(AuthProperties.Mail settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getHost());
        sender.setPort(settings.getPort());
        if (StringUtils.hasText(settings.getUsername())) {
            sender.setUsername(settings.getUsername());
            sender.setPassword(settings.getPassword());
        }
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(settings.isSmtpAuth()));
        props.put("mail.smtp.starttls.enable", String.valueOf(settings.isStartTls()));
        props.put("mail.smtp.starttls.required", String.valueOf(settings.isStartTls()));
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.put("mail.smtp.timeout", TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MS);
        return sender;
    }
}
