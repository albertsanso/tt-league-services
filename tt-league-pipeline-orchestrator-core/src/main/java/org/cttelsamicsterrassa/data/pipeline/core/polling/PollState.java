package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Instant;

/**
 * What the policy remembers about a unit: the level of the previous decision (null for a new unit), the number of
 * consecutive {@code NO_CHANGES} outcomes and the end of the last run (null when never polled).
 */
public record PollState(PolicyLevel level, int consecutiveNoChange, Instant lastRunAt) {

    public PollState {
        if (consecutiveNoChange < 0) {
            throw new IllegalArgumentException("consecutiveNoChange must not be negative");
        }
    }

    public static PollState fresh() {
        return new PollState(null, 0, null);
    }
}
