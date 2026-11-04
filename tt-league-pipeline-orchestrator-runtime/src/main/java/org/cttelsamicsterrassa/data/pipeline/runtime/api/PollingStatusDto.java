package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;

/**
 * How the orchestrator is configured to refresh each source. {@code season} and {@code zone} are the schedule ones
 * (adaptive polling uses them too) and are null when no source is scheduled. {@code tickInterval} is the fixed delay of
 * the adaptive tick (ISO-8601). Read-only: it exposes no keys, URLs or lock settings.
 */
public record PollingStatusDto(String season, String zone, Duration tickInterval, List<SourceModeDto> sources) {

    /**
     * The mode of one source: {@code ADAPTIVE}, {@code CRON} (then {@code cron} is the expression) or {@code NONE}.
     */
    public record SourceModeDto(PipelineSource source, String mode, String cron) {
    }

    static PollingStatusDto from(
            PipelineOrchestratorProperties.Schedule schedule, PipelineOrchestratorProperties.Polling polling) {
        List<SourceModeDto> modes = Arrays.stream(PipelineSource.values())
                .map(source -> mode(source, schedule, polling))
                .toList();
        boolean scheduled = modes.stream().anyMatch(mode -> !"NONE".equals(mode.mode()));
        return new PollingStatusDto(scheduled ? schedule.season() : null, scheduled ? schedule.zone() : null,
                polling.tickInterval(), modes);
    }

    private static SourceModeDto mode(
            PipelineSource source, PipelineOrchestratorProperties.Schedule schedule,
            PipelineOrchestratorProperties.Polling polling) {
        if (polling.sources().contains(source)) {
            return new SourceModeDto(source, "ADAPTIVE", null);
        }
        if (schedule.sources().containsKey(source)) {
            return new SourceModeDto(source, "CRON", schedule.cron(source));
        }
        return new SourceModeDto(source, "NONE", null);
    }
}
