package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import java.time.LocalDate;

/**
 * Filters for the match-day list. {@code from} and {@code to} select dated match days whose first-to-last match dates
 * overlap the range (the grace period is not added); results are sorted by first date (undated last), then key.
 */
public record MatchDayQuery(
        PipelineSource source,
        String season,
        MatchDayState state,
        LocalDate from,
        LocalDate to,
        int page,
        int size) {

    public static final int MAX_SIZE = 200;

    public MatchDayQuery {
        if (season != null) {
            PipelineRun.requireValidSeason(season);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }
}
