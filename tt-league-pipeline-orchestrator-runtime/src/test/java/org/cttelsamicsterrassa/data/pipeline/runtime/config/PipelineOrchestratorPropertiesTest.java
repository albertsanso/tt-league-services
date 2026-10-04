package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PipelineOrchestratorPropertiesTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsWhenAllPropertiesAreValid() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            assertThat(context).hasNotFailed();
            PipelineOrchestratorProperties properties = context.getBean(PipelineOrchestratorProperties.class);
            assertThat(properties.ingest().apiKey()).isEqualTo("key");
            assertThat(properties.platform().apiKey()).isEqualTo("platform-key");
            assertThat(properties.platform().baseUrl().getPort()).isEqualTo(8080);
            assertThat(properties.artifacts().dir().toString()).isEqualTo("artifacts");
            assertThat(properties.execution().maxConcurrentRuns()).isEqualTo(3);
            assertThat(properties.execution().recoverOnStartup()).isTrue();
        });
    }

    @Test
    void buildsTheCoreExecutionSettings() {
        runner.withPropertyValues(valid().toArray(String[]::new)).run(context -> {
            ExecutionSettings settings = context.getBean(PipelineOrchestratorProperties.class).executionSettings();
            assertThat(settings.retry().maxRetries()).isEqualTo(3);
            assertThat(settings.retry().initialBackoff()).isEqualTo(Duration.ofSeconds(30));
            assertThat(settings.retry().multiplier()).isEqualTo(2.0);
            assertThat(settings.retry().maxBackoff()).isEqualTo(Duration.ofMinutes(5));
            assertThat(settings.timeouts().ingest()).isEqualTo(Duration.ofHours(3));
            assertThat(settings.timeouts().fetchPackage()).isEqualTo(Duration.ofMinutes(10));
            assertThat(settings.timeouts().importJob()).isEqualTo(Duration.ofHours(3));
            assertThat(settings.polls().ingest()).isEqualTo(Duration.ofSeconds(15));
            assertThat(settings.polls().importJob()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @Test
    void failsWhenIngestApiKeyIsBlank() {
        assertFails(replace("tt.pipeline.ingest.api-key", "   "), "apiKey");
    }

    @Test
    void failsWhenPlatformApiKeyIsMissing() {
        assertFails(without("tt.pipeline.platform.api-key"), "apiKey");
    }

    @Test
    void failsWhenJwtSecretIsTooShort() {
        assertFails(replace("tt.pipeline.security.jwt-secret", "short"), "jwtSecret");
    }

    @Test
    void failsWhenPlatformUrlIsMissing() {
        assertFails(without("tt.pipeline.platform.base-url"), "platform");
    }

    @Test
    void failsWhenIngestUrlIsBlank() {
        assertFails(replace("tt.pipeline.ingest.base-url", ""), "baseUrl");
    }

    @Test
    void failsWhenTheArtifactsDirIsMissing() {
        assertFails(without("tt.pipeline.artifacts.dir"), "artifacts");
    }

    @Test
    void failsOnAZeroOrMissingTimeoutOrPollInterval() {
        assertFails(replace("tt.pipeline.platform.connect-timeout", "PT0S"), "platform.connect-timeout");
        assertFails(replace("tt.pipeline.platform.read-timeout", "-PT1S"), "platform.read-timeout");
        assertFails(replace("tt.pipeline.platform.poll-interval", "PT0S"), "platform.poll-interval");
        assertFails(without("tt.pipeline.ingest.connect-timeout"), "ingest.connect-timeout");
        assertFails(replace("tt.pipeline.ingest.read-timeout", "PT0S"), "ingest.read-timeout");
        assertFails(replace("tt.pipeline.ingest.poll-interval", "PT0S"), "ingest.poll-interval");
        assertFails(replace("tt.pipeline.execution.timeouts.ingest", "PT0S"), "timeouts.ingest");
        assertFails(replace("tt.pipeline.execution.timeouts.fetch-package", "PT0S"), "timeouts.fetch-package");
        assertFails(replace("tt.pipeline.execution.timeouts.import-job", "PT0S"), "timeouts.import-job");
        assertFails(replace("tt.pipeline.execution.initial-backoff", "PT0S"), "initial-backoff");
    }

    @Test
    void failsOnInvalidRetrySettings() {
        assertFails(replace("tt.pipeline.execution.max-retries", "-1"), "maxRetries");
        assertFails(replace("tt.pipeline.execution.max-retries", "11"), "maxRetries");
        assertFails(replace("tt.pipeline.execution.backoff-multiplier", "0.5"), "multiplier");
        assertFails(replace("tt.pipeline.execution.max-backoff", "PT10S"), "maxBackoff");
    }

    @Test
    void failsWhenMaxConcurrentRunsIsOutsideOneToThree() {
        assertFails(replace("tt.pipeline.execution.max-concurrent-runs", "0"), "max-concurrent-runs");
        assertFails(replace("tt.pipeline.execution.max-concurrent-runs", "4"), "max-concurrent-runs");
    }

    @Test
    void failsWhenRecoverOnStartupIsMissing() {
        assertFails(without("tt.pipeline.execution.recover-on-startup"), "recover-on-startup");
    }

    private void assertFails(List<String> properties, String expectedFragment) {
        runner.withPropertyValues(properties.toArray(String[]::new)).run(context -> {
            assertThat(context).hasFailed();
            StringBuilder messages = new StringBuilder();
            for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            assertThat(messages.toString()).contains(expectedFragment);
        });
    }

    private static List<String> valid() {
        return new ArrayList<>(List.of(
                "tt.pipeline.platform.base-url=http://localhost:8080",
                "tt.pipeline.platform.api-key=platform-key",
                "tt.pipeline.platform.connect-timeout=PT10S",
                "tt.pipeline.platform.read-timeout=PT5M",
                "tt.pipeline.platform.poll-interval=PT10S",
                "tt.pipeline.ingest.base-url=http://localhost:8000",
                "tt.pipeline.ingest.api-key=key",
                "tt.pipeline.ingest.connect-timeout=PT10S",
                "tt.pipeline.ingest.read-timeout=PT1M",
                "tt.pipeline.ingest.poll-interval=PT15S",
                "tt.pipeline.artifacts.dir=artifacts",
                "tt.pipeline.execution.max-retries=3",
                "tt.pipeline.execution.initial-backoff=PT30S",
                "tt.pipeline.execution.backoff-multiplier=2",
                "tt.pipeline.execution.max-backoff=PT5M",
                "tt.pipeline.execution.timeouts.ingest=PT3H",
                "tt.pipeline.execution.timeouts.fetch-package=PT10M",
                "tt.pipeline.execution.timeouts.import-job=PT3H",
                "tt.pipeline.execution.max-concurrent-runs=3",
                "tt.pipeline.execution.recover-on-startup=true",
                "tt.pipeline.security.jwt-secret=" + VALID_SECRET));
    }

    private static List<String> replace(String key, String value) {
        List<String> properties = without(key);
        properties.add(key + "=" + value);
        return properties;
    }

    private static List<String> without(String key) {
        List<String> properties = valid();
        properties.removeIf(entry -> entry.startsWith(key + "="));
        return properties;
    }

    @Configuration
    @EnableConfigurationProperties(PipelineOrchestratorProperties.class)
    static class PropertiesConfig {
    }
}
