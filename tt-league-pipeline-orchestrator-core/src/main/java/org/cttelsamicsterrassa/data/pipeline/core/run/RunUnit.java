package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.UUID;

/**
 * One scope of a run (one ingest group, or the whole season) with its own ingest, fetch and import chain. Immutable;
 * every transition returns a new instance. Progress is not part of the state machine: {@link #withProgress} replaces it
 * while the unit is running and every transition clears it.
 */
public final class RunUnit {

    public static final int MAX_LABEL = 256;

    private final UUID id;
    private final UUID runId;
    private final int ordinal;
    private final String unitKey;
    private final String label;
    private final RunScope scope;
    private final UnitStatus status;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final String ingestRunId;
    private final UUID importJobId;
    private final RunError error;
    private final UnitProgress progress;
    private final long version;

    private RunUnit(
            UUID id,
            UUID runId,
            int ordinal,
            String unitKey,
            String label,
            RunScope scope,
            UnitStatus status,
            Instant startedAt,
            Instant finishedAt,
            String ingestRunId,
            UUID importJobId,
            RunError error,
            UnitProgress progress,
            long version) {
        this.id = Checks.required(id, "id");
        this.runId = Checks.required(runId, "runId");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must not be negative");
        }
        this.ordinal = ordinal;
        this.unitKey = Checks.nonBlankMax(unitKey, "unitKey", 64);
        this.label = Checks.nonBlankMax(label, "label", MAX_LABEL);
        this.scope = Checks.required(scope, "scope");
        this.status = Checks.required(status, "status");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.ingestRunId = Checks.optionalMax(ingestRunId, "ingestRunId", 64);
        this.importJobId = importJobId;
        this.error = error;
        this.progress = progress;
        this.version = Checks.nonNegative(version, "version");
        validateInvariants();
    }

    /** A new unit waiting for its turn. */
    public static RunUnit plan(UUID id, UUID runId, int ordinal, String unitKey, String label, RunScope scope) {
        return new RunUnit(id, runId, ordinal, unitKey, label, scope, UnitStatus.PENDING, null, null, null, null,
                null, null, 0L);
    }

    /** Rebuilds a stored unit; used by adapters only. Re-validates every invariant. */
    public static RunUnit restore(
            UUID id,
            UUID runId,
            int ordinal,
            String unitKey,
            String label,
            RunScope scope,
            UnitStatus status,
            Instant startedAt,
            Instant finishedAt,
            String ingestRunId,
            UUID importJobId,
            RunError error,
            UnitProgress progress,
            long version) {
        return new RunUnit(id, runId, ordinal, unitKey, label, scope, status, startedAt, finishedAt, ingestRunId,
                importJobId, error, progress, version);
    }

    public RunUnit startIngest(String ingestRunId, Instant at) {
        Checks.nonBlank(ingestRunId, "ingestRunId");
        return move(UnitStatus.RUNNING_INGEST, at, at, null, ingestRunId, importJobId, null);
    }

    /**
     * Points the unit at a new ingest run when its INGEST step is retried. Not a status transition: the unit stays
     * RUNNING_INGEST with its start time and version, and its progress restarts.
     */
    public RunUnit restartIngest(String newIngestRunId, Instant at) {
        Checks.nonBlankMax(newIngestRunId, "ingestRunId", 64);
        Checks.required(at, "at");
        if (status != UnitStatus.RUNNING_INGEST) {
            throw new IllegalUnitTransitionException(id, status, UnitStatus.RUNNING_INGEST);
        }
        return copy(status, startedAt, finishedAt, newIngestRunId, importJobId, error, null);
    }

    public RunUnit noChanges(Instant at) {
        return move(UnitStatus.NO_CHANGES, at, startedAt, at, ingestRunId, importJobId, null);
    }

    public RunUnit packed(Instant at) {
        if (status != UnitStatus.RUNNING_INGEST) {
            throw new IllegalUnitTransitionException(id, status, UnitStatus.PACKED);
        }
        return move(UnitStatus.PACKED, at, startedAt, null, ingestRunId, importJobId, null);
    }

    /** Skips ingest for a replay: PENDING to PACKED. */
    public RunUnit startReplay(Instant at) {
        if (status != UnitStatus.PENDING) {
            throw new IllegalUnitTransitionException(id, status, UnitStatus.PACKED);
        }
        return move(UnitStatus.PACKED, at, at, null, ingestRunId, importJobId, null);
    }

    public RunUnit startImport(UUID importJobId, Instant at) {
        Checks.required(importJobId, "importJobId");
        return move(UnitStatus.IMPORTING, at, startedAt, null, ingestRunId, importJobId, null);
    }

    public RunUnit succeed(Instant at) {
        return move(UnitStatus.SUCCEEDED, at, startedAt, at, ingestRunId, importJobId, null);
    }

    public RunUnit partial(Instant at) {
        return move(UnitStatus.PARTIAL, at, startedAt, at, ingestRunId, importJobId, null);
    }

    public RunUnit fail(RunError error, Instant at) {
        Checks.required(error, "error");
        return move(UnitStatus.FAILED, at, startedAt, at, ingestRunId, importJobId, error);
    }

    /** A pending unit that will not run because the run was aborted. */
    public RunUnit skip(RunError error, Instant at) {
        Checks.required(error, "error");
        return move(UnitStatus.SKIPPED, at, null, at, ingestRunId, importJobId, error);
    }

    /**
     * Replaces the progress of a running unit; not a transition, the version is unchanged. Allowed only while the
     * unit is RUNNING_INGEST, PACKED or IMPORTING.
     */
    public RunUnit withProgress(UnitProgress newProgress) {
        Checks.required(newProgress, "progress");
        if (!status.isRunning()) {
            throw new IllegalStateException("Unit " + id + " is " + status + " and cannot report progress");
        }
        return copy(status, startedAt, finishedAt, ingestRunId, importJobId, error, newProgress);
    }

    private RunUnit move(
            UnitStatus next,
            Instant at,
            Instant newStartedAt,
            Instant newFinishedAt,
            String newIngestRunId,
            UUID newImportJobId,
            RunError newError) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalUnitTransitionException(id, status, next);
        }
        Checks.required(at, "at");
        return new RunUnit(id, runId, ordinal, unitKey, label, scope, next, newStartedAt, newFinishedAt,
                newIngestRunId, newImportJobId, newError, null, version);
    }

    private RunUnit copy(
            UnitStatus newStatus,
            Instant newStartedAt,
            Instant newFinishedAt,
            String newIngestRunId,
            UUID newImportJobId,
            RunError newError,
            UnitProgress newProgress) {
        return new RunUnit(id, runId, ordinal, unitKey, label, scope, newStatus, newStartedAt, newFinishedAt,
                newIngestRunId, newImportJobId, newError, newProgress, version);
    }

    private void validateInvariants() {
        if (UnitKey.SEASON.equals(unitKey) != scope.isFullSeason()) {
            throw new IllegalArgumentException("The season unit key is required exactly for a full-season scope");
        }
        if (scope.filters().size() > 1 && !UnitKey.LEGACY.equals(unitKey)) {
            throw new IllegalArgumentException("Only a legacy unit may have more than one scope filter");
        }
        if (scope.filters().size() == 1 && !UnitKey.LEGACY.equals(unitKey)
                && !unitKey.equals(UnitKey.of(scope.filters().get(0)))) {
            throw new IllegalArgumentException("unitKey does not match the unit scope");
        }
        if ((status == UnitStatus.PENDING || status == UnitStatus.SKIPPED) && startedAt != null) {
            throw new IllegalArgumentException("startedAt must be absent for " + status + " units");
        }
        if (status != UnitStatus.PENDING && status != UnitStatus.SKIPPED && status != UnitStatus.FAILED
                && startedAt == null) {
            throw new IllegalArgumentException("startedAt is required for " + status + " units");
        }
        if (status.isTerminal() != (finishedAt != null)) {
            throw new IllegalArgumentException("finishedAt must be present exactly for terminal units");
        }
        boolean errored = status == UnitStatus.FAILED || status == UnitStatus.SKIPPED;
        if (errored != (error != null)) {
            throw new IllegalArgumentException("error must be present exactly for FAILED and SKIPPED units");
        }
        boolean importJobRequired =
                status == UnitStatus.IMPORTING || status == UnitStatus.SUCCEEDED || status == UnitStatus.PARTIAL;
        boolean importJobForbidden = status == UnitStatus.PENDING
                || status == UnitStatus.RUNNING_INGEST
                || status == UnitStatus.NO_CHANGES
                || status == UnitStatus.PACKED
                || status == UnitStatus.SKIPPED;
        if (importJobRequired && importJobId == null) {
            throw new IllegalArgumentException("importJobId is required for " + status + " units");
        }
        if (importJobForbidden && importJobId != null) {
            throw new IllegalArgumentException("importJobId must be absent for " + status + " units");
        }
        if (progress != null && !status.isRunning()) {
            throw new IllegalArgumentException("progress is only allowed while a unit is running");
        }
        if (finishedAt != null && startedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID runId() {
        return runId;
    }

    public int ordinal() {
        return ordinal;
    }

    public String unitKey() {
        return unitKey;
    }

    public String label() {
        return label;
    }

    public RunScope scope() {
        return scope;
    }

    public UnitStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public String ingestRunId() {
        return ingestRunId;
    }

    public UUID importJobId() {
        return importJobId;
    }

    public RunError error() {
        return error;
    }

    public UnitProgress progress() {
        return progress;
    }

    public long version() {
        return version;
    }
}
