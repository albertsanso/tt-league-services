package org.cttelsamicsterrassa.data.pipeline.core.execution;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import java.util.List;
import java.util.Objects;

/** Re-dispatches every active run, oldest first; the executor resumes each from its stored state. */
public final class RunRecovery {

    private final PipelineRunRepository runs;
    private final RunDispatcher dispatcher;

    public RunRecovery(PipelineRunRepository runs, RunDispatcher dispatcher) {
        this.runs = Objects.requireNonNull(runs, "runs is required");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher is required");
    }

    /** Returns the number of runs dispatched. */
    public int recover() {
        List<PipelineRun> active = runs.findByStatusIn(RunStatus.active());
        for (PipelineRun run : active) {
            dispatcher.dispatch(run.id());
        }
        return active.size();
    }
}
