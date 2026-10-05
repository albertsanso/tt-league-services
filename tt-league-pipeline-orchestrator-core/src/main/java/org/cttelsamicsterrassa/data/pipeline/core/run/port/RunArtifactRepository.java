package org.cttelsamicsterrassa.data.pipeline.core.run.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;

public interface RunArtifactRepository {

    RunArtifact add(RunArtifact artifact);

    List<RunArtifact> findByRunId(UUID runId);

    List<RunArtifact> findByStorageKey(String storageKey);

    /** Marks every not yet purged row of the key as purged and returns how many rows changed. */
    int markPurged(String storageKey, Instant at);
}
