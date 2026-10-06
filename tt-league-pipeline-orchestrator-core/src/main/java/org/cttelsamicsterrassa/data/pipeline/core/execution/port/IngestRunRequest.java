package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * @param correlationId the orchestrator run id, sent to ingest so both services log the same {@code runId}
 */
public record IngestRunRequest(PipelineSource source, String season, IngestMode mode, RunScope scope, boolean force,
                               UUID correlationId) {

    public IngestRunRequest {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(season, "season is required");
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(scope, "scope is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
    }

    /**
     * The request for one unit: the unit's own scope. Only the full-season unit is a snapshot; ingest rejects a scoped
     * snapshot that includes the package stage.
     */
    public static IngestRunRequest forUnit(PipelineRun run, RunUnit unit) {
        IngestMode mode = unit.scope().isFullSeason() ? IngestMode.SNAPSHOT : IngestMode.DELTA;
        return new IngestRunRequest(run.source(), run.season(), mode, unit.scope(), run.force(), run.id());
    }
}
