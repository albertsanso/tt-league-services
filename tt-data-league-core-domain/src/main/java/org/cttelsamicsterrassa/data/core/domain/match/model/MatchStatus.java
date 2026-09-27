package org.cttelsamicsterrassa.data.core.domain.match.model;

/**
 * The lifecycle of a stored {@link Match}.
 *
 * <p>{@code SCHEDULED} means the fixture is known but has not been played: no winner, game or set
 * score, lineup, or doubles pair is recorded for it. {@code PLAYED} means results are stored.
 * "Postponed" and "overdue" are derived in read models from a scheduled match's date and are not
 * values of this enum.</p>
 */
public enum MatchStatus {
    SCHEDULED,
    PLAYED
}
