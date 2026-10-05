package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.cttelsamicsterrassa.data.pipeline.core.retention.ArtifactRetentionRepository;
import org.cttelsamicsterrassa.data.pipeline.core.retention.RetainedArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class JpaArtifactRetentionRepository implements ArtifactRetentionRepository {

    private final EntityManager entityManager;

    JpaArtifactRetentionRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<RetainedArtifact> findUnpurged() {
        List<Object[]> rows = entityManager.createQuery(
                "select a, r.source, r.season, r.status from RunArtifactEntity a, PipelineRunEntity r "
                        + "where a.runId = r.id and a.purgedAt is null", Object[].class)
                .getResultList();
        List<RetainedArtifact> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            RunArtifactEntity entity = (RunArtifactEntity) row[0];
            RunStatus status = (RunStatus) row[3];
            RunArtifact artifact = new RunArtifact(entity.id, entity.runId, entity.kind, entity.storageKey,
                    entity.sha256, entity.sizeBytes, entity.createdAt, entity.purgedAt);
            result.add(new RetainedArtifact(
                    artifact, (PipelineSource) row[1], (String) row[2], RunStatus.active().contains(status)));
        }
        return result;
    }

    @Override
    public Map<PipelineSource, List<String>> seasonsBySource() {
        List<Object[]> rows = entityManager.createQuery(
                "select distinct r.source, r.season from PipelineRunEntity r", Object[].class).getResultList();
        Map<PipelineSource, List<String>> bySource = new EnumMap<>(PipelineSource.class);
        for (Object[] row : rows) {
            bySource.computeIfAbsent((PipelineSource) row[0], source -> new ArrayList<>()).add((String) row[1]);
        }
        return bySource;
    }
}
