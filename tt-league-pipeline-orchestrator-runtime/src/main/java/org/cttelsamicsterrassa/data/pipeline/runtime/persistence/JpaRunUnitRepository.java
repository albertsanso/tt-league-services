package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitProgress;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
class JpaRunUnitRepository implements RunUnitRepository {

    private final RunUnitJpaRepository units;
    private final RunScopeJson scopeJson;

    JpaRunUnitRepository(RunUnitJpaRepository units, RunScopeJson scopeJson) {
        this.units = units;
        this.scopeJson = scopeJson;
    }

    /** One transaction: a unique-ordinal violation or any other failure leaves no unit of the run behind. */
    @Override
    public List<RunUnit> addAll(List<RunUnit> planned) {
        List<RunUnitEntity> entities = new ArrayList<>(planned.size());
        for (RunUnit unit : planned) {
            RunUnitEntity entity = new RunUnitEntity(unit.id());
            copyFields(unit, entity);
            entities.add(entity);
        }
        return units.saveAllAndFlush(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public RunUnit update(RunUnit unit) {
        RunUnitEntity entity = units.findById(unit.id())
                .orElseThrow(() -> new IllegalArgumentException("Unknown unit " + unit.id()));
        if (entity.version != unit.version()) {
            throw new StaleRunException(unit.runId(), unit.id(), unit.version());
        }
        copyFields(unit, entity);
        try {
            return toDomain(units.saveAndFlush(entity));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new StaleRunException(unit.runId(), unit.id(), unit.version());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RunUnit> findById(UUID id) {
        return units.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RunUnit> findByRunId(UUID runId) {
        return units.findByRunIdOrderByOrdinalAsc(runId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<RunUnit>> findByRunIds(Collection<UUID> runIds) {
        Map<UUID, List<RunUnit>> byRun = new LinkedHashMap<>();
        if (runIds.isEmpty()) {
            return byRun;
        }
        for (RunUnitEntity entity : units.findByRunIdInOrderByRunIdAscOrdinalAsc(runIds)) {
            byRun.computeIfAbsent(entity.runId, id -> new ArrayList<>()).add(toDomain(entity));
        }
        return byRun;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<RunUnit>> findNewestFinishedBySource(
            PipelineSource source, int perKey, Set<String> unitKeys) {
        if (perKey < 1) {
            throw new IllegalArgumentException("perKey must be at least 1");
        }
        List<UUID> ids = unitKeys.isEmpty()
                ? units.findNewestFinishedIds(source.name(), perKey)
                : units.findNewestFinishedIdsForKeys(source.name(), perKey, unitKeys);
        Map<String, List<RunUnit>> byKey = new LinkedHashMap<>();
        units.findAllById(ids).stream()
                .map(this::toDomain)
                .sorted(Comparator.comparing(RunUnit::finishedAt).reversed().thenComparing(RunUnit::id))
                .forEach(unit -> byKey.computeIfAbsent(unit.unitKey(), key -> new ArrayList<>()).add(unit));
        return byKey;
    }

    private void copyFields(RunUnit unit, RunUnitEntity entity) {
        entity.runId = unit.runId();
        entity.ordinal = unit.ordinal();
        entity.unitKey = unit.unitKey();
        entity.label = unit.label();
        entity.scope = scopeJson.write(unit.scope());
        entity.status = unit.status();
        entity.startedAt = unit.startedAt();
        entity.finishedAt = unit.finishedAt();
        entity.ingestRunId = unit.ingestRunId();
        entity.importJobId = unit.importJobId();
        entity.errorCode = unit.error() == null ? null : unit.error().code();
        entity.errorMessage = unit.error() == null ? null : unit.error().message();
        UnitProgress progress = unit.progress();
        entity.progressStep = progress == null ? null : progress.step();
        entity.progressStage = progress == null ? null : progress.stage();
        entity.progressItems = progress == null ? null : progress.itemsProcessed();
        entity.progressTotal = progress == null ? null : progress.itemsTotal();
        entity.progressCurrent = progress == null ? null : progress.currentItem();
        entity.progressUpdatedAt = progress == null ? null : progress.updatedAt();
    }

    private RunUnit toDomain(RunUnitEntity entity) {
        RunError error = entity.errorCode == null && entity.errorMessage == null
                ? null
                : new RunError(entity.errorCode, entity.errorMessage);
        UnitProgress progress = entity.progressStep == null
                ? null
                : new UnitProgress(entity.progressStep, entity.progressStage, entity.progressItems,
                        entity.progressTotal, entity.progressCurrent, entity.progressUpdatedAt);
        return RunUnit.restore(entity.id, entity.runId, entity.ordinal, entity.unitKey, entity.label,
                scopeJson.read(entity.scope), entity.status, entity.startedAt, entity.finishedAt,
                entity.ingestRunId, entity.importJobId, error, progress, entity.version);
    }
}
