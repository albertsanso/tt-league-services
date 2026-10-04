package org.cttelsamicsterrassa.data.core.application.match.roundprogress.dto;

import java.time.LocalDate;

/**
 * One jornada of the round progress (FEAT-00102). The postponed, overdue, awaiting-result and undated
 * counts are disjoint subsets of {@code scheduledMatches}.
 */
public record JornadaProgressReadModel(
        int round,
        LocalDate firstDate,
        LocalDate lastDate,
        long scheduledMatches,
        long playedMatches,
        long postponedMatches,
        long overdueMatches,
        long awaitingResultMatches,
        long undatedMatches,
        boolean complete,
        boolean current,
        boolean open) {
}
