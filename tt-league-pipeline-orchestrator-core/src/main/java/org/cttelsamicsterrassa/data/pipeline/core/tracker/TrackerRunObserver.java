package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.RecomputeRequests;
import java.util.Objects;

/** Requests a recompute when a run reaches a terminal status. It only enqueues; step changes are ignored. */
public final class TrackerRunObserver implements RunObserver {

    private final RecomputeRequests requests;

    public TrackerRunObserver(RecomputeRequests requests) {
        this.requests = Objects.requireNonNull(requests, "requests is required");
    }

    @Override
    public void runChanged(PipelineRun run) {
        if (!run.status().isTerminal()) {
            return;
        }
        requests.request(run.source(), run.season(), new RunRef(run.id(), run.finishedAt()));
    }
}
