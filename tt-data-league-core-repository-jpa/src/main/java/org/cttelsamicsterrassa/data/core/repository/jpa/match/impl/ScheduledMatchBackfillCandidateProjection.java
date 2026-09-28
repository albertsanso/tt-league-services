package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import java.time.LocalDate;
import java.util.UUID;

/**
 * JPQL constructor-expression projection for {@link MatchRepositoryHelper#findScheduledBackfillCandidates}.
 * Mapped to the domain {@code ScheduledMatchBackfillCandidate} by the JPA adapter.
 */
public class ScheduledMatchBackfillCandidateProjection {

    private final UUID matchId;
    private final String competition;
    private final Integer groupNumber;
    private final int round;
    private final String phase;
    private final LocalDate matchDate;
    private final UUID homeTeamId;
    private final UUID awayTeamId;
    private final long gameCount;
    private final long lineupCount;

    public ScheduledMatchBackfillCandidateProjection(UUID matchId, String competition, Integer groupNumber, int round,
                                                      String phase, LocalDate matchDate, UUID homeTeamId,
                                                      UUID awayTeamId, long gameCount, long lineupCount) {
        this.matchId = matchId;
        this.competition = competition;
        this.groupNumber = groupNumber;
        this.round = round;
        this.phase = phase;
        this.matchDate = matchDate;
        this.homeTeamId = homeTeamId;
        this.awayTeamId = awayTeamId;
        this.gameCount = gameCount;
        this.lineupCount = lineupCount;
    }

    public UUID matchId() {
        return matchId;
    }

    public String competition() {
        return competition;
    }

    public Integer groupNumber() {
        return groupNumber;
    }

    public int round() {
        return round;
    }

    public String phase() {
        return phase;
    }

    public LocalDate matchDate() {
        return matchDate;
    }

    public UUID homeTeamId() {
        return homeTeamId;
    }

    public UUID awayTeamId() {
        return awayTeamId;
    }

    public long gameCount() {
        return gameCount;
    }

    public long lineupCount() {
        return lineupCount;
    }
}
