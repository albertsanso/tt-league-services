package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;

/** Hook for run, unit and step changes: events, tracker recompute and alerts attach here. */
public interface RunObserver {

    default void runChanged(PipelineRun run) {
    }

    /** A unit changed: a transition, or a progress update while the unit is running. */
    default void unitChanged(RunUnit unit) {
    }

    default void stepChanged(PipelineStep step) {
    }

    static RunObserver none() {
        return new RunObserver() {
        };
    }
}
