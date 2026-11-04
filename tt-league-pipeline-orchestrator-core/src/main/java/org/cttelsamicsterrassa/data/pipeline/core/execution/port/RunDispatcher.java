package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.util.UUID;

/** Hands a run to an executor thread. Must not block for the run's duration. */
public interface RunDispatcher {

    void dispatch(UUID runId);
}
