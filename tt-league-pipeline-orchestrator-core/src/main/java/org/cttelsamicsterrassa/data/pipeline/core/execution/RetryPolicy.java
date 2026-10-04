package org.cttelsamicsterrassa.data.pipeline.core.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record RetryPolicy(int maxRetries, Duration initialBackoff, double multiplier, Duration maxBackoff) {

    public RetryPolicy {
        if (maxRetries < 0 || maxRetries > 10) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 10: " + maxRetries);
        }
        Objects.requireNonNull(initialBackoff, "initialBackoff is required");
        Objects.requireNonNull(maxBackoff, "maxBackoff is required");
        if (initialBackoff.isZero() || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        if (!(multiplier >= 1)) {
            throw new IllegalArgumentException("multiplier must be at least 1: " + multiplier);
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must not be below initialBackoff");
        }
    }

    /** Empty once the retries are used up; otherwise {@code min(initial x multiplier^retriesSoFar, max)}. */
    public Optional<Duration> delayBeforeRetry(int retriesSoFar) {
        if (retriesSoFar < 0) {
            throw new IllegalArgumentException("retriesSoFar must not be negative");
        }
        if (retriesSoFar >= maxRetries) {
            return Optional.empty();
        }
        double millis = initialBackoff.toMillis() * Math.pow(multiplier, retriesSoFar);
        return Optional.of(Duration.ofMillis((long) Math.min(millis, maxBackoff.toMillis())));
    }
}
