package org.cttelsamicsterrassa.data.core.domain.match.model;

/**
 * How many days after a match's date it remains {@link CalendarMatchState#AWAITING_RESULT} before
 * becoming {@link CalendarMatchState#OVERDUE} (FEAT-00092).
 *
 * <p>The runtime always binds the value explicitly from configuration; {@link #DEFAULT} exists only
 * for tests and documentation, never as a silent fallback.</p>
 */
public record OverdueGracePeriod(int days) {

    public static final OverdueGracePeriod DEFAULT = new OverdueGracePeriod(7);

    public OverdueGracePeriod {
        if (days < 0) {
            throw new IllegalArgumentException("overdue grace days must not be negative, was " + days);
        }
    }
}