package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingPolicySettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollPolicyException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Stores the per-source polling overrides; version 0 creates, any other expected version must match. */
@Repository
@Transactional
class JpaPollPolicyRepository implements PollPolicyRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final PollPolicyJpaRepository policies;
    private final PollingSettingsJson settingsJson;

    JpaPollPolicyRepository(PollPolicyJpaRepository policies, PollingSettingsJson settingsJson) {
        this.policies = policies;
        this.settingsJson = settingsJson;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PollingPolicySettings> find(PipelineSource source) {
        return policies.findById(source).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PollingPolicySettings> findAll() {
        return policies.findAllByOrderBySourceAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public PollingPolicySettings save(
            PipelineSource source, PollingSettings settings, String updatedBy, Instant updatedAt,
            long expectedVersion) {
        Optional<PollPolicyEntity> stored = policies.findById(source);
        PollPolicyEntity entity;
        if (stored.isPresent()) {
            entity = stored.get();
            if (entity.version != expectedVersion) {
                throw new StalePollPolicyException(source, expectedVersion);
            }
        } else {
            if (expectedVersion != 0L) {
                throw new StalePollPolicyException(source, expectedVersion);
            }
            entity = new PollPolicyEntity(source);
        }
        entity.settings = settingsJson.write(settings);
        entity.updatedBy = updatedBy;
        entity.updatedAt = updatedAt;
        try {
            return toDomain(policies.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StalePollPolicyException(source, expectedVersion);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new StalePollPolicyException(source, expectedVersion);
            }
            throw e;
        }
    }

    @Override
    public boolean delete(PipelineSource source) {
        Optional<PollPolicyEntity> stored = policies.findById(source);
        stored.ifPresent(entity -> {
            policies.delete(entity);
            policies.flush();
        });
        return stored.isPresent();
    }

    private PollingPolicySettings toDomain(PollPolicyEntity entity) {
        return new PollingPolicySettings(entity.source, settingsJson.read(entity.settings), entity.version,
                entity.updatedBy, entity.updatedAt);
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
