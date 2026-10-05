package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollScheduleException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores poll schedules with optimistic versions: an existing row is updated only when its stored version equals the
 * aggregate version, otherwise (or when the row is gone, or another writer created the same unit) the write fails with
 * {@link StalePollScheduleException} and nothing changes.
 */
@Repository
@Transactional
class JpaPollScheduleRepository implements PollScheduleRepository {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FULL_REFRESH = "FULL_REFRESH";
    private static final String GROUP = "GROUP";

    private final PollScheduleJpaRepository schedules;
    private final RunScopeJson scopeJson;

    JpaPollScheduleRepository(PollScheduleJpaRepository schedules, RunScopeJson scopeJson) {
        this.schedules = schedules;
        this.scopeJson = scopeJson;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PollSchedule> findBySourceAndSeason(PipelineSource source, String season) {
        return schedules.findBySourceAndSeason(source, season).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PollSchedule> findById(UUID id) {
        return schedules.findById(id).map(this::toDomain);
    }

    @Override
    public PollSchedule save(PollSchedule schedule) {
        Optional<PollScheduleEntity> stored = schedules.findById(schedule.id());
        Instant now = Instant.now();
        PollScheduleEntity entity;
        if (stored.isPresent()) {
            entity = stored.get();
            if (entity.version != schedule.version()) {
                throw new StalePollScheduleException(schedule.id(), "Poll schedule " + schedule.id()
                        + " changed concurrently; expected version " + schedule.version());
            }
        } else {
            if (schedule.version() != 0L) {
                throw new StalePollScheduleException(schedule.id(), "Poll schedule " + schedule.id()
                        + " no longer exists; expected version " + schedule.version());
            }
            entity = new PollScheduleEntity(schedule.id());
            entity.createdAt = now;
        }
        copy(schedule, entity, now);
        try {
            return toDomain(schedules.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StalePollScheduleException(schedule.id(), "Poll schedule " + schedule.id()
                    + " changed concurrently");
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new StalePollScheduleException(schedule.id(),
                        "Another writer created the same poll unit " + schedule.scopeKey());
            }
            throw e;
        }
    }

    @Override
    public void delete(UUID id) {
        schedules.findById(id).ifPresent(entity -> {
            schedules.delete(entity);
            schedules.flush();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<PollSchedule> query(PipelineSource source, String season) {
        Sort order = Sort.by("source", "season", "scopeKey");
        List<PollScheduleEntity> found;
        if (source != null && season != null) {
            found = schedules.findBySourceAndSeason(source, season, order);
        } else if (source != null) {
            found = schedules.findBySource(source, order);
        } else if (season != null) {
            found = schedules.findBySeason(season, order);
        } else {
            found = schedules.findAll(order);
        }
        return found.stream().map(this::toDomain).toList();
    }

    private void copy(PollSchedule schedule, PollScheduleEntity entity, Instant now) {
        entity.source = schedule.source();
        entity.season = schedule.season();
        entity.scopeKey = schedule.scopeKey();
        entity.kind = schedule.isFullRefresh() ? FULL_REFRESH : GROUP;
        entity.filter = schedule.filter() == null ? null : scopeJson.write(new RunScope(List.of(schedule.filter())));
        entity.policyLevel = schedule.level();
        entity.intervalSeconds = schedule.interval() == null ? null : schedule.interval().toSeconds();
        entity.consecutiveNoChange = schedule.consecutiveNoChange();
        entity.nextRunAt = schedule.nextRunAt();
        entity.lastRunAt = schedule.lastRunAt();
        entity.pendingRunId = schedule.pendingRunId();
        entity.stoppedAt = schedule.stoppedAt();
        entity.stopReason = schedule.stopReason();
        entity.alertedAt = schedule.alertedAt();
        entity.updatedAt = now;
    }

    private PollSchedule toDomain(PollScheduleEntity entity) {
        ScopeFilter filter = null;
        if (entity.filter != null) {
            List<ScopeFilter> filters = scopeJson.read(entity.filter).filters();
            if (filters.size() != 1) {
                throw new IllegalStateException("Poll schedule " + entity.id + " must hold exactly one filter");
            }
            filter = filters.get(0);
        }
        Duration interval = entity.intervalSeconds == null ? null : Duration.ofSeconds(entity.intervalSeconds);
        return PollSchedule.restore(entity.id, entity.source, entity.season, entity.scopeKey, filter,
                entity.policyLevel, interval, entity.consecutiveNoChange, entity.nextRunAt, entity.lastRunAt,
                entity.pendingRunId, entity.stoppedAt, entity.stopReason, entity.alertedAt, entity.version);
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException hibernate
                    && UNIQUE_VIOLATION.equals(hibernate.getSQLState())) {
                return true;
            }
            if (t instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
