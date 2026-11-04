package org.cttelsamicsterrassa.data.pipeline.core.retention;

import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;

/** An unpurged artifact row with the source and season of its run and whether that run is still active. */
public record RetainedArtifact(RunArtifact artifact, PipelineSource source, String season, boolean runActive) {

    public RetainedArtifact {
        Objects.requireNonNull(artifact, "artifact is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(season, "season is required");
    }
}
