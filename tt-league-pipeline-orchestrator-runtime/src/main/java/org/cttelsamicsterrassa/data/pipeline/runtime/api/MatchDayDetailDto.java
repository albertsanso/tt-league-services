package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.List;

/**
 * A match day with its tracked matches and its timeline, oldest event first. {@code runs} are the runs that touched
 * the match day (referenced by its events or by a match's {@code reportedRunId}), newest first, without steps.
 */
public record MatchDayDetailDto(
        MatchDaySummaryDto matchDay,
        List<TrackedMatchDto> matches,
        List<MatchDayEventDto> events,
        List<RunSummaryDto> runs) {
}
