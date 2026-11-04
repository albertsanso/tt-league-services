package org.cttelsamicsterrassa.data.pipeline.core.execution.testing;

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
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;

/**
 * Enforces the version rules of the JPA adapter and the unique ordinal per run. It registers itself with the run
 * repository so run queries can filter by unit key and finished units can be grouped by source.
 */
public class InMemoryRunUnitRepository implements RunUnitRepository {

    private final InMemoryPipelineRunRepository runs;
    private final Map<UUID, RunUnit> units = new LinkedHashMap<>();

    public InMemoryRunUnitRepository(InMemoryPipelineRunRepository runs) {
        this.runs = runs;
        runs.useUnits(this);
    }

    @Override
    public synchronized List<RunUnit> addAll(List<RunUnit> planned) {
        for (RunUnit unit : planned) {
            if (units.containsKey(unit.id())) {
                throw new IllegalArgumentException("Unit already exists: " + unit.id());
            }
            boolean ordinalTaken = units.values().stream()
                    .anyMatch(other -> other.runId().equals(unit.runId()) && other.ordinal() == unit.ordinal());
            if (ordinalTaken) {
                throw new IllegalArgumentException("Ordinal " + unit.ordinal() + " is taken in run " + unit.runId());
            }
        }
        planned.forEach(unit -> units.put(unit.id(), unit));
        return planned;
    }

    @Override
    public synchronized RunUnit update(RunUnit unit) {
        RunUnit stored = units.get(unit.id());
        if (stored == null) {
            throw new IllegalArgumentException("Unknown unit: " + unit.id());
        }
        if (stored.version() != unit.version()) {
            throw new StaleRunException(unit.runId(), unit.id(), unit.version());
        }
        RunUnit next = RunUnit.restore(unit.id(), unit.runId(), unit.ordinal(), unit.unitKey(), unit.label(),
                unit.scope(), unit.status(), unit.startedAt(), unit.finishedAt(), unit.ingestRunId(),
                unit.importJobId(), unit.error(), unit.progress(), unit.version() + 1);
        units.put(unit.id(), next);
        return next;
    }

    @Override
    public synchronized Optional<RunUnit> findById(UUID id) {
        return Optional.ofNullable(units.get(id));
    }

    @Override
    public synchronized List<RunUnit> findByRunId(UUID runId) {
        return units.values().stream()
                .filter(unit -> unit.runId().equals(runId))
                .sorted(Comparator.comparingInt(RunUnit::ordinal))
                .toList();
    }

    @Override
    public synchronized Map<UUID, List<RunUnit>> findByRunIds(Collection<UUID> runIds) {
        Map<UUID, List<RunUnit>> byRun = new LinkedHashMap<>();
        for (UUID runId : runIds) {
            List<RunUnit> found = findByRunId(runId);
            if (!found.isEmpty()) {
                byRun.put(runId, found);
            }
        }
        return byRun;
    }

    @Override
    public synchronized Map<String, List<RunUnit>> findNewestFinishedBySource(
            PipelineSource source, int perKey, Set<String> unitKeys) {
        Map<String, List<RunUnit>> byKey = new LinkedHashMap<>();
        units.values().stream()
                .filter(unit -> unit.status().isTerminal() && unit.status() != UnitStatus.SKIPPED)
                .filter(unit -> unitKeys.isEmpty() || unitKeys.contains(unit.unitKey()))
                .filter(unit -> runs.findById(unit.runId()).map(run -> run.source() == source).orElse(false))
                .sorted(Comparator.comparing(RunUnit::finishedAt).reversed())
                .forEach(unit -> {
                    List<RunUnit> list = byKey.computeIfAbsent(unit.unitKey(), key -> new ArrayList<>());
                    if (list.size() < perKey) {
                        list.add(unit);
                    }
                });
        return byKey;
    }

    /** Used by the in-memory run repository for the unit key filter of a run query. */
    synchronized boolean runHasUnitKey(UUID runId, String unitKey) {
        return units.values().stream()
                .anyMatch(unit -> unit.runId().equals(runId) && unit.unitKey().equals(unitKey));
    }

    public synchronized List<RunUnit> all() {
        return List.copyOf(units.values());
    }
}
