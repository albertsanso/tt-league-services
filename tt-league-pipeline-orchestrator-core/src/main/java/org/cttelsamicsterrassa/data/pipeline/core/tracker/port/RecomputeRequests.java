package org.cttelsamicsterrassa.data.pipeline.core.tracker.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.RunRef;

/** Asks for a tracker recompute without waiting for it; the call must never block or throw. */
public interface RecomputeRequests {

    /** {@code run} is null for a periodic recompute. */
    void request(PipelineSource source, String season, RunRef run);
}
