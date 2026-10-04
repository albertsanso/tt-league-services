package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.List;

/** A match day with its tracked matches and its timeline, oldest event first. */
public record MatchDayDetailDto(
        MatchDaySummaryDto matchDay, List<TrackedMatchDto> matches, List<MatchDayEventDto> events) {
}
