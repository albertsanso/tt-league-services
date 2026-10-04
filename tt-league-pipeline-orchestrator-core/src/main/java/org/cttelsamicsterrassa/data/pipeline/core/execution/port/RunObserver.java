package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;

/** Hook for run and step changes: later events and tracker recompute attach here. */
public interface RunObserver {

    default void runChanged(PipelineRun run) {
    }

    default void stepChanged(PipelineStep step) {
    }

    static RunObserver none() {
        return new RunObserver() {
        };
    }
}
