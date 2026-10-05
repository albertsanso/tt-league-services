package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
class JpaPipelineStepRepository implements PipelineStepRepository {

    private final PipelineStepJpaRepository steps;

    JpaPipelineStepRepository(PipelineStepJpaRepository steps) {
        this.steps = steps;
    }

    /** A duplicate (run, kind, attempt) surfaces as a DataIntegrityViolationException from the unique key. */
    @Override
    public PipelineStep save(PipelineStep step) {
        PipelineStepEntity entity = steps.findById(step.id()).orElseGet(() -> new PipelineStepEntity(step.id()));
        entity.runId = step.runId();
        entity.kind = step.kind();
        entity.attempt = step.attempt();
        entity.status = step.status();
        entity.startedAt = step.startedAt();
        entity.finishedAt = step.finishedAt();
        entity.externalRef = step.externalRef();
        entity.outcome = step.outcome();
        entity.retryable = step.retryable();
        entity.errorCode = step.error() == null ? null : step.error().code();
        entity.errorMessage = step.error() == null ? null : step.error().message();
        entity.logRef = step.logRef();
        entity.httpErrors = step.ingestHealth() == null ? null : step.ingestHealth().httpErrors();
        entity.timeouts = step.ingestHealth() == null ? null : step.ingestHealth().timeouts();
        entity.parseErrors = step.ingestHealth() == null ? null : step.ingestHealth().parseErrors();
        entity.importJobReused = step.importJobReused();
        return toDomain(steps.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineStep> findByRunId(UUID runId) {
        return steps.findByRunIdOrderByStartedAtAscAttemptAsc(runId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<PipelineStep>> findByRunIds(Collection<UUID> runIds) {
        Map<UUID, List<PipelineStep>> byRun = new LinkedHashMap<>();
        if (runIds.isEmpty()) {
            return byRun;
        }
        for (PipelineStepEntity entity : steps.findByRunIdInOrderByStartedAtAscAttemptAsc(runIds)) {
            byRun.computeIfAbsent(entity.runId, id -> new ArrayList<>()).add(toDomain(entity));
        }
        return byRun;
    }

    private PipelineStep toDomain(PipelineStepEntity entity) {
        RunError error = entity.errorCode == null && entity.errorMessage == null
                ? null
                : new RunError(entity.errorCode, entity.errorMessage);
        IngestHealth health = entity.httpErrors == null
                ? null
                : new IngestHealth(entity.httpErrors, entity.timeouts, entity.parseErrors);
        return PipelineStep.restore(
                entity.id, entity.runId, entity.kind, entity.attempt, entity.status, entity.startedAt,
                entity.finishedAt, entity.externalRef, entity.outcome, entity.retryable, error, entity.logRef, health,
                entity.importJobReused);
    }
}
