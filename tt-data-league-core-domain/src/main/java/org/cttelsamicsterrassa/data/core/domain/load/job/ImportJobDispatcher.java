package org.cttelsamicsterrassa.data.core.domain.load.job;

import java.util.UUID;

/**
 * Hands a persisted {@code QUEUED} job to the runtime that executes jobs one at a time, by calling
 * {@link ImportJobService#execute(UUID)}. Implemented by the runtime module.
 */
public interface ImportJobDispatcher {

    void dispatch(UUID jobId);
}
