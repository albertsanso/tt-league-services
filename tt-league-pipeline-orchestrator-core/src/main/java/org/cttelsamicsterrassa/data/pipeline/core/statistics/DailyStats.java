package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/**
 * The stored figures of one source and local day (definitions in {@link StatisticsRules}).
 * {@code avgTimeToReport} is null when no result arrived that day.
 */
public record DailyStats(
        LocalDate date,
        PipelineSource source,
        int runs,
        int failures,
        int matchesReported,
        Duration avgTimeToReport,
        int pendingEndOfDay,
        Instant computedAt) {

    public DailyStats {
        Objects.requireNonNull(date, "date is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(computedAt, "computedAt is required");
        if (runs < 0 || failures < 0 || matchesReported < 0 || pendingEndOfDay < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
        if (failures > runs) {
            throw new IllegalArgumentException("failures must not exceed runs");
        }
        if (avgTimeToReport != null && avgTimeToReport.isNegative()) {
            throw new IllegalArgumentException("avgTimeToReport must not be negative");
        }
    }
}
