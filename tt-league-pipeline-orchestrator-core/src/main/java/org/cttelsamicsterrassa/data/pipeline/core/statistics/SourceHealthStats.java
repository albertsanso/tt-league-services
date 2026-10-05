package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.LocalDate;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/**
 * Source failures reported by finished ingest attempts, per day and source plus the totals per source.
 * {@code healthUnknown} counts attempts that carry no health data (finished before the data existed, or an older
 * ingest service).
 */
public record SourceHealthStats(List<DayHealth> days, List<SourceHealth> totals) {

    public SourceHealthStats {
        days = List.copyOf(days);
        totals = List.copyOf(totals);
    }

    public record DayHealth(
            LocalDate date,
            PipelineSource source,
            long httpErrors,
            long timeouts,
            long parseErrors,
            int ingestAttempts,
            int sourceUnavailable,
            int healthUnknown) {}

    public record SourceHealth(
            PipelineSource source,
            long httpErrors,
            long timeouts,
            long parseErrors,
            int ingestAttempts,
            int sourceUnavailable,
            int healthUnknown) {}
}
