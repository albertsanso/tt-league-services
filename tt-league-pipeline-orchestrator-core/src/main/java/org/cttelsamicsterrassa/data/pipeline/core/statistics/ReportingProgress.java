package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;

/**
 * How one match day fills with results over its window ({@code firstDate} to {@code lastDate + graceDays}). Counts
 * leave out ignored matches: {@code pending = active - reported - postponed}. There is one point per date from the
 * first date to the earlier of the window end and today.
 */
public record ReportingProgress(
        UUID matchDayId,
        MatchDayKey key,
        MatchDayState state,
        LocalDate windowStart,
        LocalDate windowEnd,
        int active,
        int reported,
        int postponed,
        int pending,
        List<Point> points) {

    public ReportingProgress {
        points = List.copyOf(points);
    }

    public record Point(LocalDate date, int reported, int pending) {}
}
