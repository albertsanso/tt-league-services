package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * An operator's decision that a {@code SCHEDULED} match is overdue (FEAT-00092).
 *
 * <p>This is the only persisted calendar data; it is an explicit manual input, separate from import
 * data, and is ignored once the match is {@link MatchStatus#PLAYED}.</p>
 */
public record MatchOverdueMark(UUID matchId, ZonedDateTime markedAt, String markedBy) {

    public MatchOverdueMark {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(markedAt, "markedAt");
        if (markedBy == null || markedBy.isBlank()) {
            throw new IllegalArgumentException("markedBy must not be blank");
        }
    }
}