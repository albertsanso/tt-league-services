package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A {@link Match} eligible to be marked {@link MatchStatus#SCHEDULED} by the backfill rule: no
 * winner, no game or set result, and no game carrying a result of its own. Used for report output
 * and the write summary.
 */
public record ScheduledMatchBackfillCandidate(
        UUID matchId,
        String competition,
        Integer groupNumber,
        int round,
        String phase,
        LocalDate matchDate,
        UUID homeTeamId,
        UUID awayTeamId,
        int gameCount,
        int lineupCount) {

    public ScheduledMatchBackfillCandidate {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(homeTeamId, "homeTeamId");
        Objects.requireNonNull(awayTeamId, "awayTeamId");
    }
}
