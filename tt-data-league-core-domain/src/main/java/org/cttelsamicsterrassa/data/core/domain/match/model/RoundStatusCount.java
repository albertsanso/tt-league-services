package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.util.Objects;

/**
 * One grouped row read from storage: how many matches of a {@link MatchStatus} a given round of one
 * competition, group and phase holds. Repositories produce these rows; {@link RoundProgressCalculator}
 * turns them into {@link RoundProgress} values.
 */
public record RoundStatusCount(
        String competition,
        Integer groupNumber,
        String phase,
        int round,
        MatchStatus status,
        long matches) {

    public RoundStatusCount {
        Objects.requireNonNull(status, "status");
        if (matches < 1) {
            throw new IllegalArgumentException("matches must be at least 1, was " + matches);
        }
    }
}
