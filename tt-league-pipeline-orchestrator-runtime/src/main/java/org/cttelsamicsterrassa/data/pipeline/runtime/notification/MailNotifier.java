package org.cttelsamicsterrassa.data.pipeline.runtime.notification;

import java.util.Objects;
import java.util.Properties;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.NotificationException;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notifier;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * The SMTP adapter of the {@link Notifier} port. The {@link JavaMailSender} is private to this class and deliberately
 * not a bean: a {@code JavaMailSender} bean would add Boot's mail health indicator, and an unreachable SMTP server
 * must not turn {@code /actuator/health} DOWN. A failure becomes a {@link NotificationException} that carries only the
 * exception simple name and the SMTP reply code, never message text, credentials or the body.
 */
public final class MailNotifier implements Notifier {

    static final int CONNECTION_TIMEOUT_MILLIS = 10_000;
    static final int READ_TIMEOUT_MILLIS = 30_000;
    static final int WRITE_TIMEOUT_MILLIS = 30_000;

    private static final Logger LOG = LoggerFactory.getLogger(MailNotifier.class);
    private static final String FOOTER = "\n\n--\nSent by the tt-league pipeline orchestrator.";

    private final JavaMailSender sender;
    private final String from;
    private final String[] to;
    private final String subjectPrefix;

    public MailNotifier(PipelineOrchestratorProperties.Mail mail) {
        this(sender(mail), mail);
    }

    MailNotifier(JavaMailSender sender, PipelineOrchestratorProperties.Mail mail) {
        this.sender = Objects.requireNonNull(sender, "sender is required");
        Objects.requireNonNull(mail, "mail is required");
        this.from = Objects.requireNonNull(mail.from(), "mail.from is required");
        this.to = mail.to().toArray(String[]::new);
        this.subjectPrefix = mail.subjectPrefix();
    }

    private static JavaMailSenderImpl sender(PipelineOrchestratorProperties.Mail mail) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.host());
        sender.setPort(mail.port());
        Properties properties = sender.getJavaMailProperties();
        if (mail.username() != null) {
            sender.setUsername(mail.username());
            sender.setPassword(mail.password());
            properties.put("mail.smtp.auth", "true");
        }
        properties.put("mail.smtp.starttls.enable", String.valueOf(mail.starttls()));
        properties.put("mail.smtp.starttls.required", String.valueOf(mail.starttls()));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(CONNECTION_TIMEOUT_MILLIS));
        properties.put("mail.smtp.timeout", String.valueOf(READ_TIMEOUT_MILLIS));
        properties.put("mail.smtp.writetimeout", String.valueOf(WRITE_TIMEOUT_MILLIS));
        return sender;
    }

    @Override
    public void send(Notification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subjectPrefix.isEmpty()
                ? notification.subject()
                : subjectPrefix + " " + notification.subject());
        message.setText(notification.body() + FOOTER);
        try {
            sender.send(message);
        } catch (MailException e) {
            throw new NotificationException(reason(e));
        }
        LOG.info("notification sent: {} ({} recipients)", notification.subject(), to.length);
    }

    private static String reason(MailException failure) {
        String reason = failure.getClass().getSimpleName();
        if (failure instanceof MailSendException send) {
            for (Exception failed : send.getMessageExceptions()) {
                if (failed instanceof SMTPSendFailedException smtp) {
                    return reason + " (SMTP " + smtp.getReturnCode() + ")";
                }
            }
        }
        for (Throwable t = failure.getCause(); t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof SMTPSendFailedException smtp) {
                return reason + " (SMTP " + smtp.getReturnCode() + ")";
            }
        }
        return reason;
    }
}
