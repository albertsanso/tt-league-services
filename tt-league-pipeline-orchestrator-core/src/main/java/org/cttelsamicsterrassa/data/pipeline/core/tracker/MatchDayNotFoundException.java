package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.UUID;

/** The match day, or the match within it, does not exist. */
public class MatchDayNotFoundException extends RuntimeException {

    private final UUID matchDayId;
    private final UUID matchId;

    public MatchDayNotFoundException(UUID matchDayId) {
        super("Match day " + matchDayId + " not found");
        this.matchDayId = matchDayId;
        this.matchId = null;
    }

    public MatchDayNotFoundException(UUID matchDayId, UUID matchId) {
        super("Match " + matchId + " not found in match day " + matchDayId);
        this.matchDayId = matchDayId;
        this.matchId = matchId;
    }

    public UUID matchDayId() {
        return matchDayId;
    }

    /** The missing match, or null when the match day itself is missing. */
    public UUID matchId() {
        return matchId;
    }
}
