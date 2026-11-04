package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The tracker rules, one place each. Match states are only mapped from the platform calendar state, never derived
 * from dates or rounds here.
 */
public final class TrackerRules {

    private TrackerRules() {
    }

    /** Maps the platform {@code calendarState}; an unknown value is a protocol error. */
    public static TrackedMatchStatus mapCalendarState(String calendarState) {
        if (calendarState == null) {
            throw new IllegalArgumentException("calendarState is required");
        }
        return switch (calendarState) {
            case "PLAYED" -> TrackedMatchStatus.REPORTED;
            case "AWAITING_RESULT" -> TrackedMatchStatus.AWAITING_RESULT;
            case "OVERDUE" -> TrackedMatchStatus.OVERDUE;
            case "POSTPONED" -> TrackedMatchStatus.POSTPONED;
            case "UPCOMING", "UNDATED" -> TrackedMatchStatus.SCHEDULED;
            default -> throw new IllegalArgumentException("Unknown calendarState: " + calendarState);
        };
    }

    /**
     * The state a match day should have after a recompute. A CLOSED day stays CLOSED here (see {@link
     * #shouldReopen}). An UPCOMING day opens once its window started or any match is awaiting a result, overdue or
     * reported; an open day with at least one match and every match resolved closes.
     */
    public static MatchDayState targetState(MatchDay day, List<MatchTracking> matches, LocalDate today) {
        if (day.state() == MatchDayState.CLOSED) {
            return MatchDayState.CLOSED;
        }
        boolean open = day.state() == MatchDayState.OPEN || shouldOpen(day, matches, today);
        if (!open) {
            return MatchDayState.UPCOMING;
        }
        return canAutoClose(matches) ? MatchDayState.CLOSED : MatchDayState.OPEN;
    }

    /** True once every match is resolved; false for a day without matches. */
    public static boolean canAutoClose(List<MatchTracking> matches) {
        return !matches.isEmpty() && matches.stream().allMatch(MatchTracking::isResolved);
    }

    /** Only a day the tracker closed itself reopens, when a match is unresolved again. */
    public static boolean shouldReopen(MatchDay day, List<MatchTracking> matches) {
        return day.state() == MatchDayState.CLOSED
                && day.closeReason() == CloseReason.ALL_RESOLVED
                && !matches.isEmpty()
                && !canAutoClose(matches);
    }

    /**
     * The completion category of a match day from its state and its active (non-ignored) match counts. An UPCOMING
     * day is FUTURE; otherwise any overdue match makes it HAS_OVERDUE (also when closed manually), a day whose active
     * matches are all reported (or without active matches) is COMPLETE, and everything else is IN_PROGRESS.
     */
    public static MatchDayCompletion completion(MatchDayState state, Map<TrackedMatchStatus, Integer> activeCounts) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (activeCounts == null) {
            throw new IllegalArgumentException("activeCounts is required");
        }
        if (state == MatchDayState.UPCOMING) {
            return MatchDayCompletion.FUTURE;
        }
        if (activeCounts.getOrDefault(TrackedMatchStatus.OVERDUE, 0) > 0) {
            return MatchDayCompletion.HAS_OVERDUE;
        }
        int total = activeCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (activeCounts.getOrDefault(TrackedMatchStatus.REPORTED, 0) == total) {
            return MatchDayCompletion.COMPLETE;
        }
        return MatchDayCompletion.IN_PROGRESS;
    }

    private static boolean shouldOpen(MatchDay day, List<MatchTracking> matches, LocalDate today) {
        if (day.window().hasStarted(today)) {
            return true;
        }
        return matches.stream().anyMatch(match -> match.status() == TrackedMatchStatus.AWAITING_RESULT
                || match.status() == TrackedMatchStatus.OVERDUE
                || match.status() == TrackedMatchStatus.REPORTED);
    }
}
