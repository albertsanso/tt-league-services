package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;

public interface RunArtifactRepository {

    RunArtifact add(RunArtifact artifact);

    List<RunArtifact> findByRunId(UUID runId);
}
