package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InMemoryRunArtifactRepository implements RunArtifactRepository {

    private final List<RunArtifact> artifacts = new ArrayList<>();

    @Override
    public synchronized RunArtifact add(RunArtifact artifact) {
        artifacts.add(artifact);
        return artifact;
    }

    @Override
    public synchronized List<RunArtifact> findByRunId(UUID runId) {
        return artifacts.stream().filter(artifact -> artifact.runId().equals(runId)).toList();
    }
}
