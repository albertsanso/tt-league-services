package org.cttelsamicsterrassa.data.core.repository.jpa.match.impl;

import org.cttelsamicsterrassa.data.core.repository.jpa.match.MatchStatus;

/**
 * JPQL constructor-expression projection for {@link MatchRepositoryHelper#countByRoundAndStatus}.
 * Mapped to the domain {@code RoundStatusCount} by the JPA adapter, which then hands the rows to
 * {@code RoundProgressCalculator}; the progress rule is never expressed in JPQL.
 */
public class RoundStatusCountProjection {

    private final String competition;
    private final Integer groupNumber;
    private final String phase;
    private final Integer round;
    private final MatchStatus status;
    private final Long matches;

    public RoundStatusCountProjection(String competition, Integer groupNumber, String phase, Integer round,
                                      MatchStatus status, Long matches) {
        this.competition = competition;
        this.groupNumber = groupNumber;
        this.phase = phase;
        this.round = round;
        this.status = status;
        this.matches = matches;
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

    public Long matches() {
        return matches;
    }
}
