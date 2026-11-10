package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One row of the ingest {@code match-days-status} report, in the ingest scope vocabulary. Blank strings become null.
 * {@code region} and {@code regionType} are the optional attributes the ingest adds next to {@code territory}
 * (FEAT-00121); they are read for display and never take part in scope identity ({@code UnitKey}, {@code PollUnit}).
 */
public record IngestStatusRow(
        String season,
        String category,
        String group,
        String phase,
        String gender,
        String territory,
        int matchDay,
        String status,
        LocalDateTime firstMatchAt,
        LocalDateTime lastMatchAt,
        String region,
        String regionType) {

    /** A row of an ingest that reports no region (every report written before FEAT-00121). */
    public IngestStatusRow(String season, String category, String group, String phase, String gender,
                           String territory, int matchDay, String status, LocalDateTime firstMatchAt,
                           LocalDateTime lastMatchAt) {
        this(season, category, group, phase, gender, territory, matchDay, status, firstMatchAt, lastMatchAt,
                null, null);
    }

    public IngestStatusRow {
        Objects.requireNonNull(season, "season is required");
        category = blankToNull(category);
        group = blankToNull(group);
        phase = blankToNull(phase);
        gender = blankToNull(gender);
        territory = blankToNull(territory);
        if (matchDay < 1) {
            throw new IllegalArgumentException("matchDay must be at least 1");
        }
        status = blankToNull(status);
        region = blankToNull(region);
        regionType = blankToNull(regionType);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
