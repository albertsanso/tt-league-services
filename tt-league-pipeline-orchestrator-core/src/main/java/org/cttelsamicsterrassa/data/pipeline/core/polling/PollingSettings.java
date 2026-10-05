package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Duration;
import java.util.Objects;

/**
 * Intervals and thresholds of the adaptive polling policy. {@code matchDayStartOffset} is how long after the first
 * start of the day the {@code MATCH_DAY} level begins. Intervals must not get shorter for slower levels.
 */
public record PollingSettings(
        Duration matchDay,
        Duration matchDayStartOffset,
        Duration dayAfter,
        Duration daysTwoToSeven,
        Duration open,
        Duration overdue,
        int overdueStopAfterDays,
        Duration fullRefresh,
        int noChangeThreshold) {

    public PollingSettings {
        positive(matchDay, "matchDay");
        positive(matchDayStartOffset, "matchDayStartOffset");
        positive(dayAfter, "dayAfter");
        positive(daysTwoToSeven, "daysTwoToSeven");
        positive(open, "open");
        positive(overdue, "overdue");
        positive(fullRefresh, "fullRefresh");
        if (matchDay.compareTo(dayAfter) > 0 || dayAfter.compareTo(daysTwoToSeven) > 0
                || daysTwoToSeven.compareTo(open) > 0 || open.compareTo(fullRefresh) > 0) {
            throw new IllegalArgumentException(
                    "intervals must satisfy matchDay <= dayAfter <= daysTwoToSeven <= open <= fullRefresh");
        }
        if (overdue.compareTo(fullRefresh) > 0) {
            throw new IllegalArgumentException("overdue must not exceed fullRefresh");
        }
        if (overdueStopAfterDays < 1) {
            throw new IllegalArgumentException("overdueStopAfterDays must be at least 1");
        }
        if (noChangeThreshold < 1) {
            throw new IllegalArgumentException("noChangeThreshold must be at least 1");
        }
    }

    public static PollingSettings defaults() {
        return new PollingSettings(Duration.ofHours(2), Duration.ofHours(2), Duration.ofHours(3),
                Duration.ofHours(12), Duration.ofHours(24), Duration.ofHours(24), 21, Duration.ofDays(7), 3);
    }

    /** Base interval of a level; {@code STOPPED} has none. */
    public Duration interval(PolicyLevel level) {
        return switch (level) {
            case MATCH_DAY -> matchDay;
            case DAY_AFTER -> dayAfter;
            case DAYS_2_TO_7 -> daysTwoToSeven;
            case OPEN -> open;
            case OVERDUE -> overdue;
            case FULL_REFRESH -> fullRefresh;
            case STOPPED -> throw new IllegalArgumentException("Level STOPPED has no interval");
        };
    }

    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration");
        }
    }
}
