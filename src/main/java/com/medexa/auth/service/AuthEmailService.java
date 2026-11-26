package com.medexa.auth.service;

import com.medexa.auth.config.MailProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final MailProperties mailProperties;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    public void sendEmailVerification(String recipientEmail, String fullName, String rawToken) {
        String verifyUrl = frontendBaseUrl.replaceAll("/+$", "") + "/auth/verify-email?token=" + rawToken;
        String subject = "Verify your Medexa email address";
        String body = """
                Hello %s,

                Welcome to Medexa. Please verify your email address using the link below:

                %s

                This verification link expires in 24 hours. If you did not create this account, you can safely ignore this message.
                """.formatted(fullName, verifyUrl);

        dispatchEmail(recipientEmail, subject, body, "EMAIL_VERIFICATION", verifyUrl);
    }

    public void sendPasswordReset(String recipientEmail, String fullName, String rawToken) {
        String resetUrl = frontendBaseUrl.replaceAll("/+$", "") + "/auth/reset-password?token=" + rawToken;
        String subject = "Reset your Medexa password";
        String body = """
                Hello %s,

                We received a request to reset your Medexa password. Use the link below within 30 minutes:

                %s

                If you did not request a password reset, please ignore this message.
                """.formatted(fullName, resetUrl);

        dispatchEmail(recipientEmail, subject, body, "PASSWORD_RESET", resetUrl);
    }

    private void dispatchEmail(String to, String subject, String body, String type, String actionUrl) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailProperties.isEnabled() && mailSender != null) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailProperties.getFromAddress());
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Dispatched {} email to {}", type, maskEmail(to));
            return;
        }

        // In local development when SMTP is disabled, log the action URL at DEBUG only.
        log.info("Mail dispatch disabled (MAIL_ENABLED=false); skipping SMTP send for {} to {}", type, maskEmail(to));
        log.debug("[DEV-MAIL] {} action URL for {}: {}", type, maskEmail(to), actionUrl);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
