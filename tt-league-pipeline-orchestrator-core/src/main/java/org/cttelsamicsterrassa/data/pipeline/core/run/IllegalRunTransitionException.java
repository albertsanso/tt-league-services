package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.UUID;

public class IllegalRunTransitionException extends IllegalStateException {

    private final UUID runId;
    private final RunStatus from;
    private final RunStatus to;

    public IllegalRunTransitionException(UUID runId, RunStatus from, RunStatus to) {
        super("Run " + runId + " cannot transition from " + from + " to " + to);
        this.runId = runId;
        this.from = from;
        this.to = to;
    }

    public UUID runId() {
        return runId;
    }

    public RunStatus from() {
        return from;
    }

    public RunStatus to() {
        return to;
    }
}
