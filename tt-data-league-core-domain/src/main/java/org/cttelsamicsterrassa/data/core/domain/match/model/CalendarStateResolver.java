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
 *   <li>{@link CalendarMatchState#OVERDUE} — the grace period, which starts counting the day after
 *       the match date, has elapsed (with 7 days, a match of the 3rd is overdue on the 11th);</li>
 *   <li>{@link CalendarMatchState#AWAITING_RESULT} — it is at least the day after the match date but
 *       still inside the grace period;</li>
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
        LocalDate graceStart = dayAfterMatch(match);
        if (!today.isBefore(graceStart.plusDays(grace.days()))) {
            return CalendarMatchState.OVERDUE;
        }
        if (!today.isBefore(graceStart)) {
            return CalendarMatchState.AWAITING_RESULT;
        }
        return CalendarMatchState.UPCOMING;
    }

    /**
     * Whether an operator may manually mark the match overdue: only a dated SCHEDULED match, and only
     * from the day after its date (the same day the grace period starts counting).
     */
    public static boolean canMarkOverdue(Match match, LocalDate today) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(today, "today");
        return match.getStatus() == MatchStatus.SCHEDULED
                && match.getDateTime() != null
                && !today.isBefore(dayAfterMatch(match));
    }

    private static LocalDate dayAfterMatch(Match match) {
        return match.getDateTime().withZoneSameInstant(Match.COMPETITION_ZONE).toLocalDate().plusDays(1);
    }
}