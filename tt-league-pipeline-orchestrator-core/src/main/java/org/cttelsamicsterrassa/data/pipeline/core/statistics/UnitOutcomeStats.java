package org.cttelsamicsterrassa.data.pipeline.core.statistics;

import java.time.Duration;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/**
 * Terminal units by outcome per source and unit key over the range, with the average duration of the units that ran
 * (skipped units have none). Units without data are left out.
 */
public record UnitOutcomeStats(List<UnitOutcomes> units) {

    public UnitOutcomeStats {
        units = List.copyOf(units);
    }

    /**
     * @param label the label of the newest unit with that key
     * @param averageDuration null when no unit of the key ran
     */
    public record UnitOutcomes(
            PipelineSource source,
            String unitKey,
            String label,
            int succeeded,
            int noChanges,
            int partial,
            int failed,
            int skipped,
            Duration averageDuration) {}
}
