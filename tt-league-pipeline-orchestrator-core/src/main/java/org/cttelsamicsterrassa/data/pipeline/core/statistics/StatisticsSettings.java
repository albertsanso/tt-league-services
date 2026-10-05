package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Where the statistics day starts and how the daily job behaves. {@code zone} groups every figure by local day;
 * {@code backfillDays} is how many past days the first catch-up aggregates (0 to 366).
 */
public record StatisticsSettings(ZoneId zone, LocalTime dailyAt, int backfillDays) {

    public static final int MAX_BACKFILL_DAYS = 366;

    public StatisticsSettings {
        Objects.requireNonNull(zone, "zone is required");
        Objects.requireNonNull(dailyAt, "dailyAt is required");
        if (backfillDays < 0 || backfillDays > MAX_BACKFILL_DAYS) {
            throw new IllegalArgumentException("backfillDays must be between 0 and " + MAX_BACKFILL_DAYS);
        }
    }
}
