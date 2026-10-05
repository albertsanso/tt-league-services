package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import java.time.LocalDate;

/**
 * Filters for the match-day list. {@code from} and {@code to} select dated match days whose first-to-last match dates
 * overlap the range (the grace period is not added); {@code competition} and {@code phase} are exact matches;
 * {@code undated} keeps only match days without dates and excludes {@code from}/{@code to}. Results are sorted by
 * first date (undated last), then key.
 */
public record MatchDayQuery(
        PipelineSource source,
        String season,
        MatchDayState state,
        String competition,
        String phase,
        boolean undated,
        LocalDate from,
        LocalDate to,
        int page,
        int size) {

    public static final int MAX_SIZE = 200;

    public MatchDayQuery {
        if (season != null) {
            PipelineRun.requireValidSeason(season);
        }
        if (competition != null) {
            TrackerChecks.nonBlankMax(competition, "competition", 255);
        }
        if (phase != null) {
            TrackerChecks.nonBlankMax(phase, "phase", 255);
        }
        if (undated && (from != null || to != null)) {
            throw new IllegalArgumentException("undated cannot be combined with from or to");
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
