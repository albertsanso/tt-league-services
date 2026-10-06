package org.cttelsamicsterrassa.data.pipeline.core.execution;

import java.util.Set;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;

/**
 * The only place that decides whether a failed unit aborts the rest of its run. A failure that says the ingest
 * service, the platform or the artifact store is unavailable, or that the executor itself broke, would only repeat for
 * every following unit, so the remaining pending units are skipped instead. Pure: no I/O and no clock.
 */
public final class UnitExecutionRules {

    private static final Set<String> ABORTING = Set.of(
            FailureCode.INGEST_UNAVAILABLE.name(),
            FailureCode.INGEST_BUSY.name(),
            FailureCode.PLATFORM_UNAVAILABLE.name(),
            FailureCode.ARTIFACT_STORE_FAILED.name(),
            FailureCode.INTERNAL_ERROR.name());

    private UnitExecutionRules() {
    }

    /** True when a unit that failed with this {@code RunError.code} aborts the pending units of its run. */
    public static boolean abortsRemaining(String errorCode) {
        return ABORTING.contains(errorCode);
    }

    /** The error recorded on a pending unit skipped because {@code failed} aborted the run. */
    public static RunError skipError(RunUnit failed) {
        return new RunError(FailureCode.UNIT_SKIPPED.name(),
                "skipped after " + failed.error().code() + " on unit " + failed.label());
    }
}
