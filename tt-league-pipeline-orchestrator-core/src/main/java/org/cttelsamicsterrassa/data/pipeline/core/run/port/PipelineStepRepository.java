package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;

public interface PipelineStepRepository {

    /** Inserts or updates by id; a second step with the same run, kind and attempt is rejected. */
    PipelineStep save(PipelineStep step);

    /** Ordered by start time, then attempt. */
    List<PipelineStep> findByRunId(UUID runId);
}
