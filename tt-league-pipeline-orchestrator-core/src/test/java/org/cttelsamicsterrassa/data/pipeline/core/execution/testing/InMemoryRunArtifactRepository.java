package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import java.time.Instant;
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

    @Override
    public synchronized List<RunArtifact> findByUnitId(UUID unitId) {
        return artifacts.stream().filter(artifact -> artifact.unitId().equals(unitId)).toList();
    }

    @Override
    public synchronized List<RunArtifact> findByStorageKey(String storageKey) {
        return artifacts.stream().filter(artifact -> artifact.storageKey().equals(storageKey)).toList();
    }

    @Override
    public synchronized int markPurged(String storageKey, Instant at) {
        int changed = 0;
        for (int i = 0; i < artifacts.size(); i++) {
            RunArtifact artifact = artifacts.get(i);
            if (artifact.storageKey().equals(storageKey) && !artifact.isPurged()) {
                artifacts.set(i, new RunArtifact(artifact.id(), artifact.runId(), artifact.unitId(), artifact.kind(),
                        artifact.storageKey(), artifact.sha256(), artifact.sizeBytes(), artifact.createdAt(), at));
                changed++;
            }
        }
        return changed;
    }

    public synchronized List<RunArtifact> all() {
        return List.copyOf(artifacts);
    }
}
