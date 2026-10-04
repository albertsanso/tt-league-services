package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Non-concurrent {@link ImportRunRegistry} test double with the system-wide single active run rule. Tests can
 * occupy the slot with {@link #occupy()} to simulate another import.
 */
public final class FakeImportRunRegistry implements ImportRunRegistry {
    private final Map<UUID, ImportRunSnapshot> runs = new LinkedHashMap<>();
    private UUID activeRun;
    private int registrationAttempts;

    /** Registers a queued run of an unrelated import resource and returns its run id. */
    public UUID occupy() {
        return registerQueued(UUID.randomUUID(), ImportSource.RFETM, "2025-2026").orElseThrow().runId();
    }

    /** Completes the run started with {@link #occupy()}. */
    public void release(UUID runId) {
        complete(runId, ImportRunStatus.SUCCESS, ImportRunProgress.zero(), null, null);
    }

    public int registrationAttempts() {
        return registrationAttempts;
    }

    @Override
    public Optional<ImportRunSnapshot> registerQueued(UUID importResourceId, ImportSource source, String season) {
        registrationAttempts++;
        if (activeRun != null) {
            return Optional.empty();
        }
        UUID runId = UUID.randomUUID();
        ImportRunSnapshot snapshot = ImportRunSnapshot.queued(runId, importResourceId, source, season);
        runs.put(runId, snapshot);
        activeRun = runId;
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
    public Optional<ImportRunSnapshot> complete(UUID runId, ImportRunStatus terminalStatus,
                                                ImportRunProgress progress, ImportProcessResult result,
                                                String errorDetail) {
        Optional<ImportRunSnapshot> updated = update(runId,
                snapshot -> snapshot.complete(terminalStatus, progress, result, errorDetail));
        if (runId.equals(activeRun)) {
            activeRun = null;
        }
        return updated;
    }

    @Override
    public Optional<ImportRunSnapshot> findByRunId(UUID runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public Optional<ImportRunSnapshot> findActiveByImportResourceId(UUID importResourceId) {
        return Optional.ofNullable(activeRun).map(runs::get)
                .filter(snapshot -> snapshot.importResourceId().equals(importResourceId));
    }

    @Override
    public boolean hasActiveRun() {
        return activeRun != null;
    }

    private Optional<ImportRunSnapshot> update(UUID runId, UnaryOperator<ImportRunSnapshot> mutator) {
        ImportRunSnapshot current = runs.get(runId);
        if (current == null) {
            return Optional.empty();
        }
        ImportRunSnapshot updated = mutator.apply(current);
        runs.put(runId, updated);
        return Optional.of(updated);
    }
}
