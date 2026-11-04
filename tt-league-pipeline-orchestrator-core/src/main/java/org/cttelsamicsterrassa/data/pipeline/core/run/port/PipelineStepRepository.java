package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;

public interface PipelineStepRepository {

    /** Inserts or updates by id; a second step with the same unit, kind and attempt is rejected. */
    PipelineStep save(PipelineStep step);

    /** Ordered by start time, then attempt. */
    List<PipelineStep> findByRunId(UUID runId);

    /** Same per-run order as {@link #findByRunId}; runs without steps are absent from the map. */
    Map<UUID, List<PipelineStep>> findByRunIds(Collection<UUID> runIds);
}
