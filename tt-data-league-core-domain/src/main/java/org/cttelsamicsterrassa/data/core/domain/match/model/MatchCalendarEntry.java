package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Slim projection of a stored match for the round-progress read (FEAT-00102): just what
 * {@link CalendarStateResolver} needs, without loading teams, clubs or lineups.
 *
 * <p>{@code matchDate} is {@code null} for undated matches and is already expressed in
 * {@link Match#COMPETITION_ZONE}.</p>
 *
 * <p>This is a calendar read only: it must never feed statistics, search, community counts, or any
 * PLAYED-only view (FEAT-00079).</p>
 */
public record MatchCalendarEntry(
        UUID matchId,
        String competition,
        Integer groupNumber,
        String phase,
        int round,
        MatchStatus status,
        LocalDate matchDate) {

    public MatchCalendarEntry {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(status, "status");
        if (round < 1) {
            throw new IllegalArgumentException("round must be at least 1, was " + round);
        }
    }
}
