package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRequests;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import java.util.Objects;

/** Requests an alert evaluation when a run reaches a terminal status. It only enqueues; step changes are ignored. */
public final class AlertRunObserver implements RunObserver {

    private final AlertRequests requests;

    public AlertRunObserver(AlertRequests requests) {
        this.requests = Objects.requireNonNull(requests, "requests is required");
    }

    @Override
    public void runChanged(PipelineRun run) {
        if (run.status().isTerminal()) {
            requests.request();
        }
    }
}
