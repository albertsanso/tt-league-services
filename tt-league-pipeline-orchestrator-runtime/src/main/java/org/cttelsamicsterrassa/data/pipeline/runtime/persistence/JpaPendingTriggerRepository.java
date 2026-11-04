package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.List;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerExistsException;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
class JpaPendingTriggerRepository implements PendingTriggerRepository {

    private final PendingTriggerJpaRepository triggers;
    private final RunScopeJson scopeJson;

    JpaPendingTriggerRepository(PendingTriggerJpaRepository triggers, RunScopeJson scopeJson) {
        this.triggers = triggers;
        this.scopeJson = scopeJson;
    }

    /** The primary key allows one pending trigger per source; a second insert fails with the unique violation. */
    @Override
    public PendingTrigger add(PendingTrigger trigger) {
        PendingTriggerEntity entity = new PendingTriggerEntity(trigger.source());
        entity.season = trigger.season();
        entity.scopeType = trigger.scopeType();
        entity.filters = scopeJson.write(new RunScope(trigger.filters()));
        entity.force = trigger.force();
        entity.requestedBy = trigger.requestedBy();
        entity.requestedAt = trigger.requestedAt();
        try {
            triggers.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new PendingTriggerExistsException(trigger.source(), e);
        }
        return trigger;
    }

    @Override
    public Optional<PendingTrigger> take(PipelineSource source) {
        Optional<PendingTriggerEntity> found = triggers.findForUpdate(source);
        found.ifPresent(entity -> {
            triggers.delete(entity);
            triggers.flush();
        });
        return found.map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingTrigger> findAll() {
        return triggers.findAllByOrderByRequestedAtAsc().stream().map(this::toDomain).toList();
    }

    private PendingTrigger toDomain(PendingTriggerEntity entity) {
        return new PendingTrigger(entity.source, entity.season, entity.scopeType,
                scopeJson.read(entity.filters).filters(), entity.force, entity.requestedBy, entity.requestedAt);
    }
}
