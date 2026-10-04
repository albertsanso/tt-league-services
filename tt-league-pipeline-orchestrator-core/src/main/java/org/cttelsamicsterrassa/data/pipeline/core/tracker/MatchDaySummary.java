package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.EnumMap;
import java.util.Map;

/** A match day with its match counts per tracked status and the number of ignored matches. */
public record MatchDaySummary(MatchDay day, Map<TrackedMatchStatus, Integer> countsByStatus, int ignoredCount) {

    public MatchDaySummary {
        TrackerChecks.required(day, "day");
        TrackerChecks.required(countsByStatus, "countsByStatus");
        EnumMap<TrackedMatchStatus, Integer> complete = new EnumMap<>(TrackedMatchStatus.class);
        for (TrackedMatchStatus status : TrackedMatchStatus.values()) {
            complete.put(status, countsByStatus.getOrDefault(status, 0));
        }
        countsByStatus = Map.copyOf(complete);
    }
}
