package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * JPQL constructor-expression projection for {@link MatchRepositoryHelper#findCalendarEntries}
 * (FEAT-00102). Mapped to the domain {@code MatchCalendarEntry} by the JPA adapter.
 */
public class MatchCalendarEntryProjection {

    private final UUID matchId;
    private final String competition;
    private final Integer groupNumber;
    private final String phase;
    private final Integer round;
    private final MatchStatus status;
    private final LocalDate matchDate;

    public MatchCalendarEntryProjection(UUID matchId, String competition, Integer groupNumber, String phase,
                                        Integer round, MatchStatus status, LocalDate matchDate) {
        this.matchId = matchId;
        this.competition = competition;
        this.groupNumber = groupNumber;
        this.phase = phase;
        this.round = round;
        this.status = status;
        this.matchDate = matchDate;
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

    public String phase() {
        return phase;
    }

    public Integer round() {
        return round;
    }

    public MatchStatus status() {
        return status;
    }

    public LocalDate matchDate() {
        return matchDate;
    }
}
