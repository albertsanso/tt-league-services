package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.UUID;

/**
 * One attempt of a step within a run unit. Only a RUNNING step can finish. An INGEST step may carry the source health the
 * ingest run reported; it is set only when the step finishes.
 */
public final class PipelineStep {

    private final UUID id;
    private final UUID runId;
    private final UUID unitId;
    private final StepKind kind;
    private final int attempt;
    private final StepStatus status;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final String externalRef;
    private final String outcome;
    private final Boolean retryable;
    private final RunError error;
    private final String logRef;
    private final IngestHealth ingestHealth;
    private final Boolean importJobReused;

    private PipelineStep(
            UUID id,
            UUID runId,
            UUID unitId,
            StepKind kind,
            int attempt,
            StepStatus status,
            Instant startedAt,
            Instant finishedAt,
            String externalRef,
            String outcome,
            Boolean retryable,
            RunError error,
            String logRef,
            IngestHealth ingestHealth,
            Boolean importJobReused) {
        this.id = Checks.required(id, "id");
        this.runId = Checks.required(runId, "runId");
        this.unitId = Checks.required(unitId, "unitId");
        this.kind = Checks.required(kind, "kind");
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be at least 1");
        }
        this.attempt = attempt;
        this.status = Checks.required(status, "status");
        this.startedAt = Checks.required(startedAt, "startedAt");
        this.finishedAt = finishedAt;
        this.externalRef = Checks.optionalMax(externalRef, "externalRef", 64);
        this.outcome = Checks.optionalMax(outcome, "outcome", 32);
        this.retryable = retryable;
        this.error = error;
        this.logRef = Checks.optionalMax(logRef, "logRef", 512);
        this.ingestHealth = ingestHealth;
        this.importJobReused = importJobReused;
        if ((status == StepStatus.RUNNING) != (finishedAt == null)) {
            throw new IllegalArgumentException("finishedAt must be present exactly for finished steps");
        }
        if (finishedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
        if ((status == StepStatus.FAILED) != (error != null)) {
            throw new IllegalArgumentException("error must be present exactly for FAILED steps");
        }
        if (ingestHealth != null && kind != StepKind.INGEST) {
            throw new IllegalArgumentException("ingestHealth is only allowed on INGEST steps");
        }
        if (importJobReused != null && kind != StepKind.IMPORT) {
            throw new IllegalArgumentException("importJobReused is only allowed on IMPORT steps");
        }
        if (ingestHealth != null && status == StepStatus.RUNNING) {
            throw new IllegalArgumentException("ingestHealth is only allowed on finished steps");
        }
    }

    public static PipelineStep start(
            UUID id, UUID runId, UUID unitId, StepKind kind, int attempt, Instant startedAt, String externalRef) {
        return new PipelineStep(
                id, runId, unitId, kind, attempt, StepStatus.RUNNING, startedAt, null, externalRef, null, null, null, null, null, null);
    }

    /** Rebuilds a stored step; used by adapters only. */
    public static PipelineStep restore(
            UUID id,
            UUID runId,
            UUID unitId,
            StepKind kind,
            int attempt,
            StepStatus status,
            Instant startedAt,
            Instant finishedAt,
            String externalRef,
            String outcome,
            Boolean retryable,
            RunError error,
            String logRef,
            IngestHealth ingestHealth,
            Boolean importJobReused) {
        return new PipelineStep(
                id, runId, unitId, kind, attempt, status, startedAt, finishedAt, externalRef, outcome, retryable, error,
                logRef, ingestHealth, importJobReused);
    }

    public PipelineStep succeed(Instant at, String outcome) {
        return succeed(at, outcome, null);
    }

    public PipelineStep succeed(Instant at, String outcome, IngestHealth health) {
        requireRunning(StepStatus.SUCCEEDED);
        Checks.required(at, "at");
        return new PipelineStep(
                id, runId, unitId, kind, attempt, StepStatus.SUCCEEDED, startedAt, at, externalRef, outcome, retryable,
                null, logRef, health, importJobReused);
    }

    public PipelineStep fail(Instant at, String outcome, RunError error, boolean retryable) {
        return fail(at, outcome, error, retryable, null);
    }

    public PipelineStep fail(
            Instant at, String outcome, RunError error, boolean retryable, IngestHealth health) {
        requireRunning(StepStatus.FAILED);
        Checks.required(at, "at");
        Checks.required(error, "error");
        return new PipelineStep(
                id, runId, unitId, kind, attempt, StepStatus.FAILED, startedAt, at, externalRef, outcome, retryable, error,
                logRef, health, importJobReused);
    }

    /**
     * Attaches the platform import job of an IMPORT step and whether the platform returned an existing job for the
     * same content; same once-only rule as {@link #withExternalRef}.
     */
    public PipelineStep withImportJob(UUID importJobId, boolean reused) {
        Checks.required(importJobId, "importJobId");
        if (kind != StepKind.IMPORT) {
            throw new IllegalStateException("An import job can only be attached to an IMPORT step");
        }
        PipelineStep with = withExternalRef(importJobId.toString());
        return new PipelineStep(
                with.id, with.runId, with.unitId, with.kind, with.attempt, with.status, with.startedAt, null, with.externalRef,
                with.outcome, with.retryable, null, with.logRef, null, reused);
    }

    /** Attaches the external id once it is known; allowed once, while the step is RUNNING. */
    public PipelineStep withExternalRef(String ref) {
        requireRunning(StepStatus.RUNNING);
        if (externalRef != null) {
            throw new IllegalStateException("Step " + id + " already has an external reference");
        }
        if (ref == null || ref.isBlank() || ref.length() > 64) {
            throw new IllegalStateException("externalRef must be non-blank and at most 64 characters");
        }
        return new PipelineStep(
                id, runId, unitId, kind, attempt, status, startedAt, null, ref, outcome, retryable, null, logRef, null,
                importJobReused);
    }

    private void requireRunning(StepStatus target) {
        if (status != StepStatus.RUNNING) {
            throw new IllegalStateException("Step " + id + " is " + status + " and cannot move to " + target);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID runId() {
        return runId;
    }

    /** The run unit this attempt belongs to; attempts are numbered per unit and kind. */
    public UUID unitId() {
        return unitId;
    }

    public StepKind kind() {
        return kind;
    }

    public int attempt() {
        return attempt;
    }

    public StepStatus status() {
        return status;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    public String externalRef() {
        return externalRef;
    }

    public String outcome() {
        return outcome;
    }

    public Boolean retryable() {
        return retryable;
    }

    public RunError error() {
        return error;
    }

    public String logRef() {
        return logRef;
    }

    /** The source health of a finished INGEST step; null when unknown or not an INGEST step. */
    public IngestHealth ingestHealth() {
        return ingestHealth;
    }

    /**
     * Whether the platform returned an existing import job for the same content; null on non-IMPORT steps and when it
     * was not recorded.
     */
    public Boolean importJobReused() {
        return importJobReused;
    }
}
