package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ActiveRunConflictException;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
class JpaPipelineRunRepository implements PipelineRunRepository {

    static final String ACTIVE_SOURCE_INDEX = "ux_pipeline_run_active_source";
    private static final String UNIQUE_VIOLATION = "23505";

    private final PipelineRunJpaRepository runs;
    private final RunScopeJson scopeJson;

    JpaPipelineRunRepository(PipelineRunJpaRepository runs, RunScopeJson scopeJson) {
        this.runs = runs;
        this.scopeJson = scopeJson;
    }

    @Override
    public PipelineRun create(PipelineRun run) {
        PipelineRunEntity entity = new PipelineRunEntity(run.id());
        copyFields(run, entity);
        try {
            return toDomain(runs.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            if (violatesActiveSourceIndex(e)) {
                throw new ActiveRunConflictException(run.source(), e);
            }
            throw e;
        }
    }

    @Override
    public PipelineRun update(PipelineRun run) {
        PipelineRunEntity entity = runs.findById(run.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown run " + run.id()));
        if (entity.version != run.version()) {
            throw new StaleRunException(run.id(), run.version());
        }
        copyFields(run, entity);
        try {
            return toDomain(runs.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StaleRunException(run.id(), run.version());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PipelineRun> findById(UUID id) {
        return runs.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PipelineRun> findActiveBySource(PipelineSource source) {
        return runs.findFirstBySourceAndStatusIn(source, RunStatus.active()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PipelineRun> findByStatusIn(Set<RunStatus> statuses) {
        if (statuses.isEmpty()) {
            return List.of();
        }
        return runs.findByStatusInOrderByCreatedAtAsc(statuses).stream().map(this::toDomain).toList();
    }

    private static boolean violatesActiveSourceIndex(DataIntegrityViolationException e) {
        boolean uniqueViolation = false;
        boolean namesIndex = false;
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException hibernate
                    && UNIQUE_VIOLATION.equals(hibernate.getSQLState())) {
                uniqueViolation = true;
            }
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                uniqueViolation = true;
            }
            if (t.getMessage() != null && t.getMessage().contains(ACTIVE_SOURCE_INDEX)) {
                namesIndex = true;
            }
        }
        return uniqueViolation && namesIndex;
    }

    private void copyFields(PipelineRun run, PipelineRunEntity entity) {
        entity.source = run.source();
        entity.season = run.season();
        entity.scope = scopeJson.write(run.scope());
        entity.trigger = run.trigger();
        entity.requestedBy = run.requestedBy();
        entity.retryOfRunId = run.retryOfRunId();
        entity.status = run.status();
        entity.createdAt = run.createdAt();
        entity.startedAt = run.startedAt();
        entity.finishedAt = run.finishedAt();
        entity.ingestRunId = run.ingestRunId();
        entity.importJobId = run.importJobId();
        entity.errorCode = run.error() == null ? null : run.error().code();
        entity.errorMessage = run.error() == null ? null : run.error().message();
    }

    private PipelineRun toDomain(PipelineRunEntity entity) {
        RunError error = entity.errorCode == null && entity.errorMessage == null
                ? null
                : new RunError(entity.errorCode, entity.errorMessage);
        return PipelineRun.restore(
                entity.id, entity.source, entity.season, scopeJson.read(entity.scope), entity.trigger,
                entity.requestedBy, entity.retryOfRunId, entity.status, entity.createdAt, entity.startedAt,
                entity.finishedAt, entity.ingestRunId, entity.importJobId, error, entity.version);
    }
}
