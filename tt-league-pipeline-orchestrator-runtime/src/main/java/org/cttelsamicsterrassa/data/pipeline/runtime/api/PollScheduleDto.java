package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PolicyLevel;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** A poll schedule row; {@code filter} is null for the {@code FULL_REFRESH} unit and {@code interval} for a stopped one. */
public record PollScheduleDto(
        UUID id,
        PipelineSource source,
        String season,
        String kind,
        String scopeKey,
        ScopeFilterDto filter,
        PolicyLevel level,
        Duration interval,
        int consecutiveNoChange,
        Instant nextRunAt,
        Instant lastRunAt,
        UUID pendingRunId,
        Instant stoppedAt,
        String stopReason,
        long version) {

    static PollScheduleDto from(PollSchedule schedule) {
        return new PollScheduleDto(schedule.id(), schedule.source(), schedule.season(),
                schedule.isFullRefresh() ? "FULL_REFRESH" : "GROUP", schedule.scopeKey(),
                schedule.filter() == null ? null : ScopeFilterDto.from(schedule.filter()), schedule.level(),
                schedule.interval(), schedule.consecutiveNoChange(), schedule.nextRunAt(), schedule.lastRunAt(),
                schedule.pendingRunId(), schedule.stoppedAt(), schedule.stopReason(), schedule.version());
    }
}
