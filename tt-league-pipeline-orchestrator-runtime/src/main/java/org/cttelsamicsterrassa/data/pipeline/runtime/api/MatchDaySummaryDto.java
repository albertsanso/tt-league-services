package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDaySummary;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;

/**
 * A tracked match day. {@code windowEnd} is the last match date plus the platform grace days; the dates are null for
 * an undated jornada. {@code matchCounts} always holds every tracked status.
 */
public record MatchDaySummaryDto(
        UUID id,
        String source,
        String season,
        String competition,
        Integer groupNumber,
        String phase,
        int round,
        LocalDate firstDate,
        LocalDate lastDate,
        LocalDate windowEnd,
        int graceDays,
        String state,
        String closeReason,
        Instant closedAt,
        String closedBy,
        Instant openedAt,
        Instant lastRecomputedAt,
        Map<String, Integer> matchCounts,
        int ignoredMatches) {

    static MatchDaySummaryDto from(MatchDaySummary summary) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TrackedMatchStatus status : TrackedMatchStatus.values()) {
            counts.put(status.name(), summary.countsByStatus().get(status));
        }
        return from(summary.day(), counts, summary.ignoredCount());
    }

    static MatchDaySummaryDto from(MatchDay day, Map<String, Integer> counts, int ignored) {
        return new MatchDaySummaryDto(day.id(), day.key().source().name(), day.key().season(),
                day.key().competition(), day.key().groupNumber(), day.key().phase(), day.key().round(),
                day.window().firstDate(), day.window().lastDate(), day.window().end(), day.window().graceDays(),
                day.state().name(), day.closeReason() == null ? null : day.closeReason().name(), day.closedAt(),
                day.closedBy(), day.openedAt(), day.lastRecomputedAt(), counts, ignored);
    }
}
