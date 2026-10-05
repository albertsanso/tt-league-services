package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
class JpaRunArtifactRepository implements RunArtifactRepository {

    private final RunArtifactJpaRepository artifacts;

    JpaRunArtifactRepository(RunArtifactJpaRepository artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public RunArtifact add(RunArtifact artifact) {
        RunArtifactEntity entity = new RunArtifactEntity(artifact.id());
        entity.runId = artifact.runId();
        entity.kind = artifact.kind();
        entity.storageKey = artifact.storageKey();
        entity.sha256 = artifact.sha256();
        entity.sizeBytes = artifact.sizeBytes();
        entity.createdAt = artifact.createdAt();
        entity.purgedAt = artifact.purgedAt();
        return toDomain(artifacts.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunArtifact> findByRunId(UUID runId) {
        return artifacts.findByRunIdOrderByCreatedAtAsc(runId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunArtifact> findByStorageKey(String storageKey) {
        return artifacts.findByStorageKeyOrderByCreatedAtAsc(storageKey).stream().map(this::toDomain).toList();
    }

    @Override
    public int markPurged(String storageKey, Instant at) {
        return artifacts.markPurged(storageKey, at);
    }

    private RunArtifact toDomain(RunArtifactEntity entity) {
        return new RunArtifact(
                entity.id, entity.runId, entity.kind, entity.storageKey, entity.sha256, entity.sizeBytes,
                entity.createdAt, entity.purgedAt);
    }
}
