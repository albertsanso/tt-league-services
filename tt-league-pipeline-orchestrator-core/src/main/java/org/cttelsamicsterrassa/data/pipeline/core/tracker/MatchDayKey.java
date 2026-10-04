package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/**
 * Identity of one platform jornada: source, season, competition, group, phase and round. Group and phase are
 * absent for competitions that have neither.
 */
public record MatchDayKey(
        PipelineSource source, String season, String competition, Integer groupNumber, String phase, int round) {

    public MatchDayKey {
        TrackerChecks.required(source, "source");
        PipelineRun.requireValidSeason(season);
        TrackerChecks.nonBlankMax(competition, "competition", 255);
        if (groupNumber != null && groupNumber < 1) {
            throw new IllegalArgumentException("groupNumber must be at least 1");
        }
        TrackerChecks.optionalMax(phase, "phase", 255);
        if (round < 1) {
            throw new IllegalArgumentException("round must be at least 1");
        }
    }
}
