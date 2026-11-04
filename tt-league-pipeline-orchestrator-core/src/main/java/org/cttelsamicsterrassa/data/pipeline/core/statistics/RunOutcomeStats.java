package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;

/**
 * Terminal runs by outcome per day and source, and the average duration of finished step attempts (succeeded and
 * failed) per source and step kind over the range. Days and sources without data are left out.
 */
public record RunOutcomeStats(List<DayOutcomes> days, List<StepAverage> stepAverages) {

    public RunOutcomeStats {
        days = List.copyOf(days);
        stepAverages = List.copyOf(stepAverages);
    }

    public record DayOutcomes(
            LocalDate date, PipelineSource source, int succeeded, int noChanges, int partial, int failed) {}

    public record StepAverage(PipelineSource source, StepKind kind, int attempts, Duration average) {}
}
