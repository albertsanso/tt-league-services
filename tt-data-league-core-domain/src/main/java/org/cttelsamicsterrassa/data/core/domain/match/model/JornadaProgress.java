package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The progress of one jornada of a competition, group and phase (FEAT-00102). {@code postponedMatches},
 * {@code overdueMatches}, {@code awaitingResultMatches} and {@code undatedMatches} are disjoint subsets
 * of {@code scheduledMatches}; the remainder is upcoming. {@code firstDate}/{@code lastDate} are the
 * earliest and latest match dates in Europe/Madrid, both {@code null} when every match is undated.
 */
public record JornadaProgress(
        String competition,
        Integer groupNumber,
        String phase,
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
        boolean current) {

    public JornadaProgress {
        if (scheduledMatches < 0 || playedMatches < 0 || postponedMatches < 0 || overdueMatches < 0
                || awaitingResultMatches < 0 || undatedMatches < 0) {
            throw new IllegalArgumentException("match counts must not be negative");
        }
        if (postponedMatches + overdueMatches + awaitingResultMatches + undatedMatches > scheduledMatches) {
            throw new IllegalArgumentException("derived state counts cannot exceed scheduledMatches");
        }
        if ((firstDate == null) != (lastDate == null)) {
            throw new IllegalArgumentException("firstDate and lastDate must both be set or both be null");
        }
        if (firstDate != null && firstDate.isAfter(lastDate)) {
            throw new IllegalArgumentException("firstDate %s is after lastDate %s".formatted(firstDate, lastDate));
        }
    }

    /**
     * Open when any match is not played yet, or when {@code today} falls in
     * {@code [firstDate, lastDate + grace]}: a fully played jornada stays open exactly while its last
     * match could still be {@link CalendarMatchState#AWAITING_RESULT}.
     */
    public boolean isOpen(LocalDate today, OverdueGracePeriod grace) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(grace, "grace");
        if (scheduledMatches > 0) {
            return true;
        }
        if (firstDate == null) {
            return false;
        }
        return !today.isBefore(firstDate) && !today.isAfter(lastDate.plusDays(grace.days()));
    }
}
