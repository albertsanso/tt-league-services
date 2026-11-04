package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.time.LocalDateTime;
import java.util.Objects;

/** One row of the ingest {@code match-days-status} report, in the ingest scope vocabulary. Blank strings become null. */
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
        LocalDateTime lastMatchAt) {

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
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
