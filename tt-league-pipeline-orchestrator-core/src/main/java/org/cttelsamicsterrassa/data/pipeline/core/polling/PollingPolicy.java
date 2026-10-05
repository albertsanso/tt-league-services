package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;

/**
 * Pure polling rules: no I/O and no clock. The tracker is the only source of match state, so the level comes from the
 * tracked status and date of the candidate matches and nothing is re-derived here.
 */
public final class PollingPolicy {

    private static final int MAX_DOUBLINGS = 20;
    private static final long RECENT_DAYS = 7;

    /** The level, interval and next run of a unit with these candidate matches. */
    public PollDecision decide(
            PollState state, List<MatchTracking> candidates, PollingSettings settings, Instant now, ZoneId zone) {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(candidates, "candidates is required");
        Objects.requireNonNull(settings, "settings is required");
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(zone, "zone is required");
        PolicyLevel level = levelOf(candidates, settings, now, zone);
        if (level == PolicyLevel.STOPPED && state.lastRunAt() == null) {
            // A unit is polled at least once before it stops, which also lets a resumed unit run once.
            level = PolicyLevel.OVERDUE;
        }
        if (level == PolicyLevel.STOPPED) {
            return new PollDecision(level, null, null, null, PollDecision.OVERDUE_LIMIT, 0);
        }
        int counter = state.level() == level ? state.consecutiveNoChange() : 0;
        Duration base = settings.interval(level);
        Duration effective = backOff(level, base, counter, settings);
        Instant next = state.lastRunAt() == null ? now : state.lastRunAt().plus(effective);
        if (level == PolicyLevel.OPEN && state.lastRunAt() != null) {
            Instant start = nextStartAfter(candidates, state.lastRunAt(), settings.matchDayStartOffset());
            if (start != null && start.isBefore(next)) {
                next = start;
            }
        }
        return new PollDecision(level, base, effective, next, null, counter);
    }

    /** The weekly (or season-start) full-season unit: fixed interval, no back-off, never stopped. */
    public PollDecision fullRefresh(PollState state, PollingSettings settings, Instant now) {
        Objects.requireNonNull(state, "state is required");
        Duration interval = settings.interval(PolicyLevel.FULL_REFRESH);
        Instant next = state.lastRunAt() == null ? now : state.lastRunAt().plus(interval);
        return new PollDecision(PolicyLevel.FULL_REFRESH, interval, interval, next, null, state.consecutiveNoChange());
    }

    /**
     * Counter after a finished run: {@code NO_CHANGES} adds one, {@code SUCCEEDED} and {@code PARTIAL} reset it and
     * {@code FAILED} leaves it (the executor's retry rule already handles failures).
     */
    public PollState applyOutcome(PollState state, RunStatus terminal) {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(terminal, "terminal is required");
        if (!terminal.isTerminal()) {
            throw new IllegalArgumentException("Run status " + terminal + " is not terminal");
        }
        int counter = switch (terminal) {
            case NO_CHANGES -> state.consecutiveNoChange() + 1;
            case SUCCEEDED, PARTIAL -> 0;
            default -> state.consecutiveNoChange();
        };
        return new PollState(state.level(), counter, state.lastRunAt());
    }

    private PolicyLevel levelOf(List<MatchTracking> candidates, PollingSettings settings, Instant now, ZoneId zone) {
        LocalDate today = now.atZone(zone).toLocalDate();
        PolicyLevel best = null;
        Instant firstStartToday = null;
        boolean stopped = false;
        for (MatchTracking match : candidates) {
            if (match.isIgnored() || match.status() == TrackedMatchStatus.REPORTED) {
                continue;
            }
            LocalDate date = match.matchDateTime() == null ? null
                    : match.matchDateTime().atZone(zone).toLocalDate();
            if (match.status() == TrackedMatchStatus.OVERDUE) {
                long age = date == null ? 0 : ChronoUnit.DAYS.between(date, today);
                if (age > settings.overdueStopAfterDays()) {
                    stopped = true;
                } else {
                    best = urgent(best, PolicyLevel.OVERDUE);
                }
            } else if (date == null || date.isAfter(today)) {
                best = urgent(best, PolicyLevel.OPEN);
            } else {
                long age = ChronoUnit.DAYS.between(date, today);
                if (age == 0) {
                    firstStartToday = firstStartToday == null || match.matchDateTime().isBefore(firstStartToday)
                            ? match.matchDateTime() : firstStartToday;
                } else if (age == 1) {
                    best = urgent(best, PolicyLevel.DAY_AFTER);
                } else if (age <= RECENT_DAYS) {
                    best = urgent(best, PolicyLevel.DAYS_2_TO_7);
                } else {
                    best = urgent(best, PolicyLevel.OVERDUE);
                }
            }
        }
        if (firstStartToday != null) {
            boolean started = !now.isBefore(firstStartToday.plus(settings.matchDayStartOffset()));
            best = urgent(best, started ? PolicyLevel.MATCH_DAY : PolicyLevel.OPEN);
        }
        if (best != null) {
            return best;
        }
        return stopped ? PolicyLevel.STOPPED : PolicyLevel.OPEN;
    }

    private static PolicyLevel urgent(PolicyLevel current, PolicyLevel candidate) {
        return current == null || candidate.compareTo(current) < 0 ? candidate : current;
    }

    /** Doubles every {@code noChangeThreshold} consecutive no-change runs, up to the next slower level's interval. */
    private static Duration backOff(PolicyLevel level, Duration base, int counter, PollingSettings settings) {
        int doublings = Math.min(counter / settings.noChangeThreshold(), MAX_DOUBLINGS);
        if (doublings == 0) {
            return base;
        }
        Duration cap = settings.interval(level.slower());
        if (cap.compareTo(base) < 0) {
            cap = base;
        }
        Duration interval = base;
        for (int i = 0; i < doublings; i++) {
            interval = interval.multipliedBy(2);
            if (interval.compareTo(cap) >= 0) {
                return cap;
            }
        }
        return interval;
    }

    /** Earliest first start plus offset that lies after {@code after}, among dated, unresolved matches. */
    private static Instant nextStartAfter(List<MatchTracking> candidates, Instant after, Duration offset) {
        Instant earliest = null;
        for (MatchTracking match : candidates) {
            if (match.isIgnored() || match.matchDateTime() == null
                    || match.status() == TrackedMatchStatus.REPORTED
                    || match.status() == TrackedMatchStatus.OVERDUE) {
                continue;
            }
            Instant threshold = match.matchDateTime().plus(offset);
            if (threshold.isAfter(after) && (earliest == null || threshold.isBefore(earliest))) {
                earliest = threshold;
            }
        }
        return earliest;
    }
}
