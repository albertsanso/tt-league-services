package org.cttelsamicsterrassa.data.core.domain.match.model;

/**
 * Row counts from a {@link ScheduledMatchBackfillCandidate} write: how many matches were marked
 * {@link MatchStatus#SCHEDULED} and how many of their child rows were removed to preserve the
 * FEAT-00077 invariant.
 */
public record ScheduledMatchBackfillWriteResult(
        int matchesUpdated,
        int gamesDeleted,
        int lineupsDeleted,
        int setScoresDeleted,
        int doublesPairsDeleted) {
}
