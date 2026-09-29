package org.cttelsamicsterrassa.data.core.domain.match.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The single place the season-calendar state rule lives (FEAT-00092). Called on every read; the
 * resolved state is never stored.
 *
 * <p>A {@code SCHEDULED} match resolves, first match wins, to:</p>
 * <ol>
 *   <li>{@link CalendarMatchState#PLAYED} — the match has been played (a leftover manual mark is
 *       ignored);</li>
 *   <li>{@link CalendarMatchState#OVERDUE} — a manual overdue mark exists (the operator's decision
 *       wins over derivation);</li>
 *   <li>{@link CalendarMatchState#POSTPONED} — the match's round is below the group's current round;</li>
 *   <li>{@link CalendarMatchState#UNDATED} — the match has no date;</li>
 *   <li>{@link CalendarMatchState#OVERDUE} — the match date plus the grace period is before today;</li>
 *   <li>{@link CalendarMatchState#AWAITING_RESULT} — the date has passed but is still inside the grace
 *       period;</li>
 *   <li>{@link CalendarMatchState#UPCOMING} — otherwise.</li>
 * </ol>
 *
 * <p>Dates are compared in {@link Match#COMPETITION_ZONE} (Europe/Madrid).</p>
 */
public final class CalendarStateResolver {

    private CalendarStateResolver() {
    }

    public static CalendarMatchState resolve(Match match, Integer currentRound, boolean overdueMarked,
                                             LocalDate today, OverdueGracePeriod grace) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(grace, "grace");

        if (match.getStatus() == MatchStatus.PLAYED) {
            return CalendarMatchState.PLAYED;
        }
        if (overdueMarked) {
            return CalendarMatchState.OVERDUE;
        }
        if (currentRound != null && match.getRound() < currentRound) {
            return CalendarMatchState.POSTPONED;
        }
        if (match.getDateTime() == null) {
            return CalendarMatchState.UNDATED;
        }
        LocalDate matchDate = match.getDateTime().withZoneSameInstant(Match.COMPETITION_ZONE).toLocalDate();
        if (matchDate.plusDays(grace.days()).isBefore(today)) {
            return CalendarMatchState.OVERDUE;
        }
        if (matchDate.isBefore(today)) {
            return CalendarMatchState.AWAITING_RESULT;
        }
        return CalendarMatchState.UPCOMING;
    }
}