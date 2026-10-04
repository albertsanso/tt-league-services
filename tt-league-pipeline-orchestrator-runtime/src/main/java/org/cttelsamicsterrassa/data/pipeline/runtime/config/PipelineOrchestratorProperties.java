package org.cttelsamicsterrassa.data.pipeline.runtime.config;

import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.PollIntervals;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RetryPolicy;
import org.cttelsamicsterrassa.data.pipeline.core.execution.StepTimeouts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
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
        @Valid @NotNull Security security) {

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

    public record Security(@NotBlank @Size(min = 32) String jwtSecret) {
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
