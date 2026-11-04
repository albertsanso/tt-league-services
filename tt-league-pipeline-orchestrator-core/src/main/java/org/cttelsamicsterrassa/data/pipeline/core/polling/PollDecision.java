package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Result of {@link PollingPolicy}. {@code baseInterval}, {@code effectiveInterval} and {@code nextRunAt} are null
 * for {@code STOPPED}; {@code consecutiveNoChange} is the counter after a level change reset.
 */
public record PollDecision(
        PolicyLevel level,
        Duration baseInterval,
        Duration effectiveInterval,
        Instant nextRunAt,
        String stopReason,
        int consecutiveNoChange) {

    public static final String OVERDUE_LIMIT = "OVERDUE_LIMIT";

    public PollDecision {
        Objects.requireNonNull(level, "level is required");
        boolean stopped = level == PolicyLevel.STOPPED;
        if (stopped != (baseInterval == null) || stopped != (effectiveInterval == null) || stopped != (nextRunAt == null)
                || stopped != (stopReason != null)) {
            throw new IllegalArgumentException(
                    "intervals, nextRunAt and stopReason must be set exactly when the level is not STOPPED/STOPPED");
        }
        if (consecutiveNoChange < 0) {
            throw new IllegalArgumentException("consecutiveNoChange must not be negative");
        }
    }
}
