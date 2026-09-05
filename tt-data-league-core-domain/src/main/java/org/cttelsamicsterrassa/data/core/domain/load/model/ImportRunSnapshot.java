package org.cttelsamicsterrassa.data.core.domain.load.model;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable status snapshot of one asynchronous import run, keyed by {@code runId}.
 *
 * <p>Transition methods return a new snapshot rather than mutating this one, so a registry
 * implementation can safely publish and replace snapshots without exposing shared mutable state to
 * callers. Only {@link #running(ImportRunProgress)}, {@link #withProgress(ImportRunProgress)} and
 * {@link #complete(ImportRunStatus, ImportRunProgress, ImportProcessResult, String)} advance the
 * state; callers must not resurrect a run that already reached a terminal status.</p>
 */
public record ImportRunSnapshot(UUID runId, UUID importResourceId, ImportSource source, String season,
                                ImportRunStatus status, ImportRunProgress progress,
                                Optional<String> errorDetail, Optional<ImportProcessResult> result,
                                ZonedDateTime updatedAt) {

    public ImportRunSnapshot {
        errorDetail = errorDetail == null ? Optional.empty() : errorDetail;
        result = result == null ? Optional.empty() : result;
    }

    public static ImportRunSnapshot queued(UUID runId, UUID importResourceId, ImportSource source, String season) {
        return new ImportRunSnapshot(runId, importResourceId, source, season, ImportRunStatus.QUEUED,
                ImportRunProgress.zero(), Optional.empty(), Optional.empty(), ZonedDateTime.now());
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public ImportRunSnapshot running(ImportRunProgress nextProgress) {
        if (isTerminal()) {
            return this;
        }
        return new ImportRunSnapshot(runId, importResourceId, source, season, ImportRunStatus.RUNNING,
                nextProgress, Optional.empty(), Optional.empty(), ZonedDateTime.now());
    }

    public ImportRunSnapshot withProgress(ImportRunProgress nextProgress) {
        if (isTerminal()) {
            return this;
        }
        return new ImportRunSnapshot(runId, importResourceId, source, season, ImportRunStatus.RUNNING,
                nextProgress, Optional.empty(), Optional.empty(), ZonedDateTime.now());
    }

    public ImportRunSnapshot complete(ImportRunStatus terminalStatus, ImportRunProgress finalProgress,
                                      ImportProcessResult finalResult, String finalErrorDetail) {
        if (isTerminal()) {
            return this;
        }
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("Cannot complete a run with a non-terminal status: " + terminalStatus);
        }
        return new ImportRunSnapshot(runId, importResourceId, source, season, terminalStatus, finalProgress,
                Optional.ofNullable(finalErrorDetail), Optional.ofNullable(finalResult), ZonedDateTime.now());
    }
}
