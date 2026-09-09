package org.cttelsamicsterrassa.data.core.domain.load.service;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunProgress;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunSnapshot;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.Optional;
import java.util.UUID;

/**
 * Application port for creating, updating, and reading asynchronous import run snapshots.
 *
 * <p>{@link #registerQueued(UUID, ImportSource, String)} must be atomic: only one import can run
 * at a time system-wide, so it must never allow two accepted (non-terminal) runs to exist at once -
 * for the same import resource or for two different ones - even when requests arrive concurrently.
 * Implementations are free to keep runs in memory or to persist them; the initial adapter is an
 * in-memory registry wired in the API runtime.</p>
 */
public interface ImportRunRegistry {

    /**
     * Registers a new queued run for {@code importResourceId}, unless another run is already
     * active - for this import resource or for any other, since only one import may run at a time.
     *
     * @return the new queued snapshot, or empty when an active (non-terminal) run already exists,
     *         for this import resource or for any other
     */
    Optional<ImportRunSnapshot> registerQueued(UUID importResourceId, ImportSource source, String season);

    Optional<ImportRunSnapshot> markRunning(UUID runId, ImportRunProgress progress);

    Optional<ImportRunSnapshot> updateProgress(UUID runId, ImportRunProgress progress);

    Optional<ImportRunSnapshot> complete(UUID runId, ImportRunStatus terminalStatus, ImportRunProgress progress,
                                         ImportProcessResult result, String errorDetail);

    Optional<ImportRunSnapshot> findByRunId(UUID runId);

    Optional<ImportRunSnapshot> findActiveByImportResourceId(UUID importResourceId);
}
