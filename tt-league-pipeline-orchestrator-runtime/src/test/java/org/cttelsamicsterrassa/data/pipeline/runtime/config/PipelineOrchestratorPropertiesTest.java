package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(properties.platform().baseUrl().getPort()).isEqualTo(8080);
        });
    }

    @Test
    void failsWhenIngestApiKeyIsBlank() {
        assertFails(replace("tt.pipeline.ingest.api-key", "   "), "apiKey");
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
                "tt.pipeline.ingest.base-url=http://localhost:8000",
                "tt.pipeline.ingest.api-key=key",
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
