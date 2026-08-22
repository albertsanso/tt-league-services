package org.cttelsamicsterrassa.data.api.rest.auth;

import lombok.RequiredArgsConstructor;
import org.cttelsamicsterrassa.data.core.domain.auth.user.service.PasswordRecoveryService.RecoveryNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpPasswordRecoveryNotificationSender implements PasswordRecoveryNotificationSender {
    private final JavaMailSender mailSender;

    @Value("${security.password-recovery.from}")
    private String from;

    @Value("${security.password-recovery.reset-url}")
    private String resetUrl;

    @Override
    public void send(RecoveryNotification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(notification.email());
        message.setSubject("Recuperació de la contrasenya de TT League");
        message.setText("""
                Hem rebut una sol·licitud per recuperar la teva contrasenya.

                Utilitza aquest enllaç abans que caduqui:
                %s?token=%s

                Si no has fet aquesta sol·licitud, pots ignorar aquest missatge.
                """.formatted(resetUrl, notification.token()));
        mailSender.send(message);
    }
}
