package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.time.Instant;
import java.util.UUID;

/** The run a recompute follows; a periodic recompute has none. */
public record RunRef(UUID runId, Instant finishedAt) {

    public RunRef {
        TrackerChecks.required(runId, "runId");
        TrackerChecks.required(finishedAt, "finishedAt");
    }
}
