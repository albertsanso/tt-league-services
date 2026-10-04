package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.UUID;
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
        return toDomain(steps.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineStep> findByRunId(UUID runId) {
        return steps.findByRunIdOrderByStartedAtAscAttemptAsc(runId).stream().map(this::toDomain).toList();
    }

    private PipelineStep toDomain(PipelineStepEntity entity) {
        RunError error = entity.errorCode == null && entity.errorMessage == null
                ? null
                : new RunError(entity.errorCode, entity.errorMessage);
        return PipelineStep.restore(
                entity.id, entity.runId, entity.kind, entity.attempt, entity.status, entity.startedAt,
                entity.finishedAt, entity.externalRef, entity.outcome, entity.retryable, error, entity.logRef);
    }
}
