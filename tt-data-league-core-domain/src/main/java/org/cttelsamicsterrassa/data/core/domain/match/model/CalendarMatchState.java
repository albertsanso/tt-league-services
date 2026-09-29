package org.cttelsamicsterrassa.data.core.domain.match.model;

/**
 * The derived, never-stored state a {@link Match} has in the season calendar (FEAT-00092).
 *
 * <p>These values are computed on every read by {@link CalendarStateResolver}; no column, status or
 * match field stores them. The only calendar data that is persisted is the manual overdue mark
 * ({@link MatchOverdueMark}), which is an operator decision rather than a derived state.</p>
 */
public enum CalendarMatchState {
    PLAYED,
    UPCOMING,
    AWAITING_RESULT,
    UNDATED,
    OVERDUE,
    POSTPONED
}