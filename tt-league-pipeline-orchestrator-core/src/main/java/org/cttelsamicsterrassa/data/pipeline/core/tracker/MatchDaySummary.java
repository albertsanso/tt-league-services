package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.EnumMap;
import java.util.Map;

/**
 * A match day with its match counts per tracked status (ignored matches included) and, per status, how many of those
 * are ignored. The "active" counts exclude ignored matches.
 */
public record MatchDaySummary(
        MatchDay day,
        Map<TrackedMatchStatus, Integer> countsByStatus,
        Map<TrackedMatchStatus, Integer> ignoredByStatus) {

    public MatchDaySummary {
        TrackerChecks.required(day, "day");
        TrackerChecks.required(countsByStatus, "countsByStatus");
        TrackerChecks.required(ignoredByStatus, "ignoredByStatus");
        EnumMap<TrackedMatchStatus, Integer> counts = new EnumMap<>(TrackedMatchStatus.class);
        EnumMap<TrackedMatchStatus, Integer> ignored = new EnumMap<>(TrackedMatchStatus.class);
        for (TrackedMatchStatus status : TrackedMatchStatus.values()) {
            int count = countsByStatus.getOrDefault(status, 0);
            int ignoredCount = ignoredByStatus.getOrDefault(status, 0);
            if (count < 0 || ignoredCount < 0) {
                throw new IllegalArgumentException("counts must not be negative");
            }
            if (ignoredCount > count) {
                throw new IllegalArgumentException("ignored matches of " + status + " exceed its count");
            }
            counts.put(status, count);
            ignored.put(status, ignoredCount);
        }
        countsByStatus = Map.copyOf(counts);
        ignoredByStatus = Map.copyOf(ignored);
    }

    /** Ignored matches over all statuses. */
    public int ignoredCount() {
        return ignoredByStatus.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Counts per status without the ignored matches. */
    public Map<TrackedMatchStatus, Integer> activeCounts() {
        EnumMap<TrackedMatchStatus, Integer> active = new EnumMap<>(TrackedMatchStatus.class);
        for (TrackedMatchStatus status : TrackedMatchStatus.values()) {
            active.put(status, countsByStatus.get(status) - ignoredByStatus.get(status));
        }
        return Map.copyOf(active);
    }

    public int reportedCount() {
        return activeCounts().get(TrackedMatchStatus.REPORTED);
    }

    /** Active (non-ignored) matches. */
    public int totalCount() {
        return activeCounts().values().stream().mapToInt(Integer::intValue).sum();
    }

    public MatchDayCompletion completion() {
        return TrackerRules.completion(day.state(), activeCounts());
    }
}
