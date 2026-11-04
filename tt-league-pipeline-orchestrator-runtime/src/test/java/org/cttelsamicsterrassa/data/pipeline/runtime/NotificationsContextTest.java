package org.cttelsamicsterrassa.data.pipeline.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.runtime.notification.AlertDispatcher;
import org.cttelsamicsterrassa.data.pipeline.runtime.persistence.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * With a mail host set the dispatcher is enabled, yet the application exposes no {@code JavaMailSender} bean, so Boot
 * adds no mail health indicator: an unreachable SMTP server must not turn the health endpoint DOWN.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "tt.pipeline.platform.base-url=http://localhost:8080",
            "tt.pipeline.platform.api-key=test-platform-key",
            "tt.pipeline.platform.connect-timeout=PT2S",
            "tt.pipeline.platform.read-timeout=PT5S",
            "tt.pipeline.platform.poll-interval=PT1S",
            "tt.pipeline.ingest.base-url=http://localhost:8000",
            "tt.pipeline.ingest.api-key=test-ingest-key",
            "tt.pipeline.ingest.connect-timeout=PT2S",
            "tt.pipeline.ingest.read-timeout=PT5S",
            "tt.pipeline.ingest.poll-interval=PT1S",
            "tt.pipeline.artifacts.dir=${java.io.tmpdir}",
            "tt.pipeline.execution.recover-on-startup=false",
            "tt.pipeline.execution.max-retries=3",
            "tt.pipeline.execution.initial-backoff=PT30S",
            "tt.pipeline.execution.backoff-multiplier=2",
            "tt.pipeline.execution.max-backoff=PT5M",
            "tt.pipeline.execution.timeouts.ingest=PT3H",
            "tt.pipeline.execution.timeouts.fetch-package=PT10M",
            "tt.pipeline.execution.timeouts.import-job=PT3H",
            "tt.pipeline.execution.max-concurrent-runs=1",
            "spring.datasource.url=jdbc:postgresql://service-connection/unused",
            "spring.datasource.username=unused",
            "spring.datasource.password=unused",
            "tt.pipeline.statistics.zone=Europe/Madrid",
            "tt.pipeline.security.jwt-secret=0123456789abcdef0123456789abcdef",
            // an address nobody listens on: nothing is sent during the test
            "tt.pipeline.notifications.mail.host=127.0.0.1",
            "tt.pipeline.notifications.mail.port=1",
            "tt.pipeline.notifications.mail.from=pipeline@example.org",
            "tt.pipeline.notifications.mail.to=ops@example.org"
        })
@Import(PostgresTestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class NotificationsContextTest {

    @Autowired
    private AlertDispatcher alertDispatcher;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void theDispatcherIsEnabledAndNoMailSenderBeanExists() {
        assertThat(alertDispatcher.enabled()).isTrue();
        assertThat(alertDispatcher.isRunning()).isTrue();
        assertThat(context.getBeansOfType(JavaMailSender.class)).isEmpty();
    }

    @Test
    void healthIsUpAndHasNoMailComponent() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"").doesNotContainIgnoringCase("mail");
    }
}
