package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import java.util.Objects;

public record IngestRunRequest(PipelineSource source, String season, IngestMode mode, RunScope scope) {

    public IngestRunRequest {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(season, "season is required");
        Objects.requireNonNull(mode, "mode is required");
        Objects.requireNonNull(scope, "scope is required");
    }

    /** A full-season run is a snapshot; ingest rejects a scoped snapshot that includes the package stage. */
    public static IngestRunRequest forRun(PipelineRun run) {
        IngestMode mode = run.scope().isFullSeason() ? IngestMode.SNAPSHOT : IngestMode.DELTA;
        return new IngestRunRequest(run.source(), run.season(), mode, run.scope());
    }
}
