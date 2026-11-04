package org.cttelsamicsterrassa.data.pipeline.runtime.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Address;
import java.util.List;
import java.util.Map;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.NotificationException;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Mail;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailNotifierTest {

    private static final String PASSWORD = "s3cret-smtp-password";

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MailNotifier.class);

    private static Mail mail(String prefix) {
        return new Mail("smtp.example.org", 587, "ops", PASSWORD, true, "pipeline@example.org",
                List.of("ops@example.org", "admin@example.org"), prefix);
    }

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void sendsOneMessageToEveryRecipientWithThePrefixedSubjectAndAFooter() {
        MailNotifier notifier = new MailNotifier(sender, mail("[tt-pipeline]"));

        notifier.send(new Notification("FCTT: two consecutive runs failed", "Details here"));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("pipeline@example.org");
        assertThat(message.getValue().getTo()).containsExactly("ops@example.org", "admin@example.org");
        assertThat(message.getValue().getSubject()).isEqualTo("[tt-pipeline] FCTT: two consecutive runs failed");
        assertThat(message.getValue().getText()).startsWith("Details here").contains("pipeline orchestrator");
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("notification sent: FCTT: two consecutive runs failed (2 recipients)");
    }

    @Test
    void anEmptyPrefixLeavesTheSubjectAlone() {
        MailNotifier notifier = new MailNotifier(sender, mail(""));

        notifier.send(new Notification("Subject", "Body"));

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getSubject()).isEqualTo("Subject");
    }

    @Test
    void aMailFailureBecomesANotificationExceptionWithoutItsText() {
        MailNotifier notifier = new MailNotifier(sender, mail("[x]"));
        doThrow(new MailAuthenticationException("535 login failed for ops with " + PASSWORD))
                .when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notifier.send(new Notification("Subject", "Body")))
                .isInstanceOf(NotificationException.class)
                .hasMessage("MailAuthenticationException")
                .hasNoCause();
        assertThat(logs.list).isEmpty();
    }

    @Test
    void theSmtpReplyCodeIsKeptWhenTheServerRejectsTheMessage() {
        MailNotifier notifier = new MailNotifier(sender, mail("[x]"));
        SMTPSendFailedException rejected = new SMTPSendFailedException(
                "RCPT TO", 550, "550 mailbox unavailable " + PASSWORD, null, new Address[0], new Address[0],
                new Address[0]);
        doThrow(new MailSendException(Map.of("message", rejected)))
                .when(sender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notifier.send(new Notification("Subject", "Body")))
                .isInstanceOf(NotificationException.class)
                .hasMessage("MailSendException (SMTP 550)");
    }

    @Test
    void thePasswordNeverAppearsInLogsOrExceptions() {
        MailNotifier notifier = new MailNotifier(sender, mail("[x]"));
        doThrow(new MailSendException("failed with " + PASSWORD)).when(sender).send(any(SimpleMailMessage.class));

        NotificationException failure = org.junit.jupiter.api.Assertions.assertThrows(NotificationException.class,
                () -> notifier.send(new Notification("Subject", "Body")));

        assertThat(failure.getMessage()).doesNotContain(PASSWORD);
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains(PASSWORD));
        assertThat(mail("[x]").toString()).doesNotContain(PASSWORD);
    }
}
