package org.cttelsamicsterrassa.data.api.runtime.importrun;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.load.service.ImportRunRegistry;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * In-memory {@link ImportRunRegistry} adapter for the initial polling transport. Runs live only for
 * the lifetime of this JVM; a durable registry is a follow-up enhancement, not assumed here.
 *
 * <p>{@link #registerQueued(UUID, ImportSource, String)} must be atomic under concurrent
 * submissions: {@code activeRun} reserves the single system-wide "active run" slot before the new
 * run is published, so at most one accepted (non-terminal) run can exist at once, for any resource,
 * even when requests for different resources arrive concurrently.</p>
 */
@Component
public class InMemoryImportRunRegistry implements ImportRunRegistry {

    private final Map<UUID, ImportRunSnapshot> runsByRunId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeRunByResource = new ConcurrentHashMap<>();
    private final AtomicReference<UUID> activeRun = new AtomicReference<>();

    @Override
    public Optional<ImportRunSnapshot> registerQueued(UUID importResourceId, ImportSource source, String season) {
        UUID runId = UUID.randomUUID();
        if (!activeRun.compareAndSet(null, runId)) {
            return Optional.empty();
        }
        if (activeRunByResource.putIfAbsent(importResourceId, runId) != null) {
            activeRun.compareAndSet(runId, null);
            return Optional.empty();
        }
        ImportRunSnapshot snapshot = ImportRunSnapshot.queued(runId, importResourceId, source, season);
        runsByRunId.put(runId, snapshot);
        return Optional.of(snapshot);
    }

    @Override
    public Optional<ImportRunSnapshot> markRunning(UUID runId, ImportRunProgress progress) {
        return update(runId, snapshot -> snapshot.running(progress));
    }

    @Override
    public Optional<ImportRunSnapshot> updateProgress(UUID runId, ImportRunProgress progress) {
        return update(runId, snapshot -> snapshot.withProgress(progress));
    }

    @Override
    public Optional<ImportRunSnapshot> complete(UUID runId, ImportRunStatus terminalStatus, ImportRunProgress progress,
                                                ImportProcessResult result, String errorDetail) {
        Optional<ImportRunSnapshot> updated = update(runId,
                snapshot -> snapshot.complete(terminalStatus, progress, result, errorDetail));
        updated.ifPresent(snapshot -> {
            activeRunByResource.remove(snapshot.importResourceId(), runId);
            activeRun.compareAndSet(runId, null);
        });
        return updated;
    }

    @Override
    public Optional<ImportRunSnapshot> findByRunId(UUID runId) {
        return Optional.ofNullable(runsByRunId.get(runId));
    }

    @Override
    public Optional<ImportRunSnapshot> findActiveByImportResourceId(UUID importResourceId) {
        return Optional.ofNullable(activeRunByResource.get(importResourceId)).flatMap(this::findByRunId);
    }

    private Optional<ImportRunSnapshot> update(UUID runId, UnaryOperator<ImportRunSnapshot> mutator) {
        ImportRunSnapshot updated = runsByRunId.computeIfPresent(runId, (id, current) -> mutator.apply(current));
        return Optional.ofNullable(updated);
    }
}
