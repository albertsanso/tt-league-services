package org.cttelsamicsterrassa.data.core.domain.load.job;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportRunStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One ACTAS season imported by an {@link ImportJob}: the import resource it ran, the import run id once the run
 * was registered, and the run's outcome. A season that could not be run (for example because another import
 * kept the system busy) ends {@link ImportRunStatus#FAILURE} without a run id. Only the owning job changes it.
 */
public final class ImportJobSeason {
    private final UUID id;
    private final String season;
    private final UUID importResourceId;
    private Optional<UUID> importRunId;
    private ImportRunStatus status;
    private Optional<ImportProcessResult> result;
    private Optional<String> errorDetail;

    private ImportJobSeason(UUID id, String season, UUID importResourceId, Optional<UUID> importRunId,
                            ImportRunStatus status, Optional<ImportProcessResult> result,
                            Optional<String> errorDetail) {
        this.id = Objects.requireNonNull(id, "id");
        this.season = Objects.requireNonNull(season, "season");
        this.importResourceId = Objects.requireNonNull(importResourceId, "importResourceId");
        this.importRunId = Objects.requireNonNull(importRunId, "importRunId");
        this.status = Objects.requireNonNull(status, "status");
        this.result = Objects.requireNonNull(result, "result");
        this.errorDetail = Objects.requireNonNull(errorDetail, "errorDetail");
    }

    static ImportJobSeason createNew(String season, UUID importResourceId) {
        return new ImportJobSeason(UUID.randomUUID(), season, importResourceId, Optional.empty(),
                ImportRunStatus.QUEUED, Optional.empty(), Optional.empty());
    }

    public static ImportJobSeason createExisting(UUID id, String season, UUID importResourceId,
                                                 Optional<UUID> importRunId, ImportRunStatus status,
                                                 Optional<ImportProcessResult> result,
                                                 Optional<String> errorDetail) {
        return new ImportJobSeason(id, season, importResourceId, importRunId, status, result, errorDetail);
    }

    void complete(UUID runId, ImportRunStatus terminalStatus, Optional<ImportProcessResult> runResult,
                  Optional<String> runErrorDetail) {
        requireNotTerminal();
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("A season must complete with a terminal run status: " + terminalStatus);
        }
        this.importRunId = Optional.of(Objects.requireNonNull(runId, "runId"));
        this.status = terminalStatus;
        this.result = Objects.requireNonNull(runResult, "runResult");
        this.errorDetail = Objects.requireNonNull(runErrorDetail, "runErrorDetail");
    }

    void fail(String reason) {
        requireNotTerminal();
        this.status = ImportRunStatus.FAILURE;
        this.errorDetail = Optional.of(Objects.requireNonNull(reason, "reason"));
    }

    /**
     * Whether the season imported cleanly: a {@code SUCCESS} or {@code EMPTY_RESULT} run without processor
     * failures or execution issues.
     */
    public boolean isClean() {
        return isSucceeded() && result.map(value -> value.processorFailures() == 0
                && value.executionIssues().isEmpty()).orElse(true);
    }

    /** Whether the season's run ended {@code SUCCESS} or {@code EMPTY_RESULT}. */
    public boolean isSucceeded() {
        return status == ImportRunStatus.SUCCESS || status == ImportRunStatus.EMPTY_RESULT;
    }

    private void requireNotTerminal() {
        if (status.isTerminal()) {
            throw new IllegalStateException("Import job season " + season + " already ended " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getSeason() {
        return season;
    }

    public UUID getImportResourceId() {
        return importResourceId;
    }

    public Optional<UUID> getImportRunId() {
        return importRunId;
    }

    public ImportRunStatus getStatus() {
        return status;
    }

    public Optional<ImportProcessResult> getResult() {
        return result;
    }

    public Optional<String> getErrorDetail() {
        return errorDetail;
    }
}
