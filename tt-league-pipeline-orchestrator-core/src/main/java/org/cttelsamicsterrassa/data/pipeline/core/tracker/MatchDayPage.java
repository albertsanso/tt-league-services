package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.util.List;

/** One page of match-day summaries. */
public record MatchDayPage(List<MatchDaySummary> items, long total, int page, int size) {

    public MatchDayPage {
        TrackerChecks.required(items, "items");
        items = List.copyOf(items);
    }
}
