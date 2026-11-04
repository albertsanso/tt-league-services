package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;

/**
 * The only place that decides whether a unit can be retried on its own. Pure: no I/O and no clock. A step timeout
 * fails the unit ({@code FAILED}), so it is retryable like any other failure.
 */
public final class UnitRetryRules {

    private UnitRetryRules() {
    }

    /**
     * @param activeRun the active run of the unit's source, if any
     */
    public static UnitRetryEligibility check(PipelineRun run, RunUnit unit, Optional<PipelineRun> activeRun) {
        if (!run.status().isTerminal() || activeRun.isPresent()) {
            return UnitRetryEligibility.no(UnitRetryEligibility.RUN_ACTIVE);
        }
        if (unit.status() != UnitStatus.FAILED && unit.status() != UnitStatus.SKIPPED) {
            return UnitRetryEligibility.no(UnitRetryEligibility.UNIT_NOT_RETRYABLE);
        }
        return UnitRetryEligibility.yes();
    }
}
