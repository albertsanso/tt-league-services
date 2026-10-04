package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.UUID;

/** A match day or match change that its current state does not allow. */
public class IllegalMatchDayTransitionException extends IllegalStateException {

    private final UUID matchDayId;

    public IllegalMatchDayTransitionException(UUID matchDayId, String message) {
        super("Match day " + matchDayId + ": " + message);
        this.matchDayId = matchDayId;
    }

    public UUID matchDayId() {
        return matchDayId;
    }
}
