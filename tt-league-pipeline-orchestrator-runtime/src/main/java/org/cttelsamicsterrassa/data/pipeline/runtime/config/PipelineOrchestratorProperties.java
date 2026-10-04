package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.PollIntervals;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RetryPolicy;
import org.cttelsamicsterrassa.data.pipeline.core.execution.StepTimeouts;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Orchestrator configuration. Durations must be positive and the retry settings valid: violations fail in the compact
 * constructors, so binding (and startup) fails with the offending setting named.
 */
@Validated
@ConfigurationProperties("tt.pipeline")
public record PipelineOrchestratorProperties(
        @Valid @NotNull Platform platform,
        @Valid @NotNull Ingest ingest,
        @Valid @NotNull Artifacts artifacts,
        @Valid @NotNull Execution execution,
        @Valid @NotNull Security security,
        @Valid @NotNull Triggers triggers,
        @Valid @NotNull Events events) {

    /** Platform REST API; {@code apiKey} is a service credential with {@code imports:write}. */
    public record Platform(
            @NotNull URI baseUrl,
            @NotBlank String apiKey,
            Duration connectTimeout,
            Duration readTimeout,
            Duration pollInterval) {

        public Platform {
            positive(connectTimeout, "platform.connect-timeout");
            positive(readTimeout, "platform.read-timeout");
            positive(pollInterval, "platform.poll-interval");
        }
    }

    public record Ingest(
            @NotNull URI baseUrl,
            @NotBlank String apiKey,
            Duration connectTimeout,
            Duration readTimeout,
            Duration pollInterval) {

        public Ingest {
            positive(connectTimeout, "ingest.connect-timeout");
            positive(readTimeout, "ingest.read-timeout");
            positive(pollInterval, "ingest.poll-interval");
        }
    }

    public record Artifacts(@NotNull Path dir) {
    }

    public record Execution(
            Integer maxRetries,
            Duration initialBackoff,
            Double backoffMultiplier,
            Duration maxBackoff,
            @Valid @NotNull Timeouts timeouts,
            Integer maxConcurrentRuns,
            Boolean recoverOnStartup) {

        public Execution {
            required(maxRetries, "execution.max-retries");
            required(backoffMultiplier, "execution.backoff-multiplier");
            required(recoverOnStartup, "execution.recover-on-startup");
            required(maxConcurrentRuns, "execution.max-concurrent-runs");
            if (maxConcurrentRuns < 1 || maxConcurrentRuns > 3) {
                throw new IllegalArgumentException(
                        "execution.max-concurrent-runs must be between 1 and 3: " + maxConcurrentRuns);
            }
            positive(initialBackoff, "execution.initial-backoff");
            positive(maxBackoff, "execution.max-backoff");
            // validates 0 <= maxRetries <= 10, multiplier >= 1 and maxBackoff >= initialBackoff
            retryPolicy(maxRetries, initialBackoff, backoffMultiplier, maxBackoff);
        }

        public record Timeouts(Duration ingest, Duration fetchPackage, Duration importJob) {

            public Timeouts {
                positive(ingest, "execution.timeouts.ingest");
                positive(fetchPackage, "execution.timeouts.fetch-package");
                positive(importJob, "execution.timeouts.import-job");
            }
        }
    }

    /**
     * {@code jwtSecret} is the platform's {@code security.jwt.secret}: at least 32 UTF-8 bytes, like the platform
     * checks. {@code corsAllowedOrigins} are absolute http(s) origins; empty means no CORS headers.
     */
    public record Security(@NotBlank String jwtSecret, List<String> corsAllowedOrigins) {

        public Security {
            required(jwtSecret, "security.jwt-secret");
            if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("security.jwt-secret must be at least 32 UTF-8 bytes");
            }
            corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
            corsAllowedOrigins.forEach(Security::validOrigin);
        }

        private static void validOrigin(String origin) {
            URI uri;
            try {
                uri = new URI(origin);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(
                        "security.cors-allowed-origins must hold absolute http(s) origins: " + origin, e);
            }
            boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            boolean bareOrigin = uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && (uri.getPath() == null || uri.getPath().isEmpty());
            if (!httpScheme || !bareOrigin) {
                throw new IllegalArgumentException(
                        "security.cors-allowed-origins must hold absolute http(s) origins: " + origin);
            }
        }
    }

    /** What a manual trigger does when its source already has an active run. */
    public record Triggers(ConflictMode conflictMode) {

        public Triggers {
            required(conflictMode, "triggers.conflict-mode");
        }
    }

    /** Server-Sent Events stream limits. */
    public record Events(Duration heartbeatInterval, Duration emitterTimeout, Integer maxSubscribers) {

        public Events {
            positive(heartbeatInterval, "events.heartbeat-interval");
            positive(emitterTimeout, "events.emitter-timeout");
            required(maxSubscribers, "events.max-subscribers");
            if (maxSubscribers < 1 || maxSubscribers > 1000) {
                throw new IllegalArgumentException("events.max-subscribers must be between 1 and 1000");
            }
        }
    }

    /** The core settings: retry and timeouts from {@code execution}, poll intervals from the two services. */
    public ExecutionSettings executionSettings() {
        return new ExecutionSettings(
                retryPolicy(execution.maxRetries(), execution.initialBackoff(), execution.backoffMultiplier(),
                        execution.maxBackoff()),
                new StepTimeouts(execution.timeouts().ingest(), execution.timeouts().fetchPackage(),
                        execution.timeouts().importJob()),
                new PollIntervals(ingest.pollInterval(), platform.pollInterval()));
    }

    private static RetryPolicy retryPolicy(int maxRetries, Duration initial, double multiplier, Duration max) {
        return new RetryPolicy(maxRetries, initial, multiplier, max);
    }

    private static void required(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void positive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }
}
