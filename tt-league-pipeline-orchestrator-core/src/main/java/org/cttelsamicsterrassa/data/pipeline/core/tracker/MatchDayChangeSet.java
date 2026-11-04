package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Everything one operation writes, applied atomically by the repository. */
public record MatchDayChangeSet(
        List<MatchDay> days, List<MatchTracking> matches, Set<UUID> removedMatchIds, List<MatchDayEvent> events) {

    public MatchDayChangeSet {
        TrackerChecks.required(days, "days");
        TrackerChecks.required(matches, "matches");
        TrackerChecks.required(removedMatchIds, "removedMatchIds");
        TrackerChecks.required(events, "events");
        days = List.copyOf(days);
        matches = List.copyOf(matches);
        removedMatchIds = Set.copyOf(removedMatchIds);
        events = List.copyOf(events);
    }

    public boolean isEmpty() {
        return days.isEmpty() && matches.isEmpty() && removedMatchIds.isEmpty() && events.isEmpty();
    }
}
