package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable run aggregate; every transition returns a new instance. */
public final class PipelineRun {

    private static final Pattern SEASON = Pattern.compile("(\\d{4})-(\\d{4})");

    private final UUID id;
    private final PipelineSource source;
    private final String season;
    private final RunScope scope;
    private final RunTrigger trigger;
    private final String requestedBy;
    private final UUID retryOfRunId;
    private final RunStatus status;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final String ingestRunId;
    private final UUID importJobId;
    private final RunError error;
    private final long version;

    private PipelineRun(
            UUID id,
            PipelineSource source,
            String season,
            RunScope scope,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId,
            RunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String ingestRunId,
            UUID importJobId,
            RunError error,
            long version) {
        this.id = Checks.required(id, "id");
        this.source = Checks.required(source, "source");
        this.season = validSeason(season);
        this.scope = Checks.required(scope, "scope");
        this.trigger = Checks.required(trigger, "trigger");
        this.requestedBy = Checks.nonBlankMax(requestedBy, "requestedBy", 128);
        this.retryOfRunId = retryOfRunId;
        this.status = Checks.required(status, "status");
        this.createdAt = Checks.required(createdAt, "createdAt");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.ingestRunId = Checks.optionalMax(ingestRunId, "ingestRunId", 64);
        this.importJobId = importJobId;
        this.error = error;
        this.version = Checks.nonNegative(version, "version");
        validateInvariants();
    }

    public static PipelineRun queue(
            UUID id,
            PipelineSource source,
            String season,
            RunScope scope,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId,
            Instant now) {
        return new PipelineRun(
                id, source, season, scope, trigger, requestedBy, retryOfRunId, RunStatus.QUEUED, now, null, null,
                null, null, null, 0L);
    }

    /** Rebuilds a stored run; used by adapters only. Re-validates every invariant. */
    public static PipelineRun restore(
            UUID id,
            PipelineSource source,
            String season,
            RunScope scope,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId,
            RunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            String ingestRunId,
            UUID importJobId,
            RunError error,
            long version) {
        return new PipelineRun(
                id, source, season, scope, trigger, requestedBy, retryOfRunId, status, createdAt, startedAt,
                finishedAt, ingestRunId, importJobId, error, version);
    }

    public PipelineRun startIngest(String ingestRunId, Instant at) {
        Checks.nonBlank(ingestRunId, "ingestRunId");
        return move(RunStatus.RUNNING_INGEST, at, at, null, ingestRunId, importJobId, null);
    }

    public PipelineRun noChanges(Instant at) {
        return move(RunStatus.NO_CHANGES, at, startedAt, at, ingestRunId, importJobId, null);
    }

    public PipelineRun packed(Instant at) {
        return move(RunStatus.PACKED, at, startedAt, null, ingestRunId, importJobId, null);
    }

    public PipelineRun startImport(UUID importJobId, Instant at) {
        Checks.required(importJobId, "importJobId");
        return move(RunStatus.IMPORTING, at, startedAt, null, ingestRunId, importJobId, null);
    }

    public PipelineRun succeed(Instant at) {
        return move(RunStatus.SUCCEEDED, at, startedAt, at, ingestRunId, importJobId, null);
    }

    public PipelineRun partial(Instant at) {
        return move(RunStatus.PARTIAL, at, startedAt, at, ingestRunId, importJobId, null);
    }

    public PipelineRun fail(RunError error, Instant at) {
        Checks.required(error, "error");
        return move(RunStatus.FAILED, at, startedAt, at, ingestRunId, importJobId, error);
    }

    private PipelineRun move(
            RunStatus next,
            Instant at,
            Instant newStartedAt,
            Instant newFinishedAt,
            String newIngestRunId,
            UUID newImportJobId,
            RunError newError) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalRunTransitionException(id, status, next);
        }
        Checks.required(at, "at");
        return new PipelineRun(
                id, source, season, scope, trigger, requestedBy, retryOfRunId, next, createdAt, newStartedAt,
                newFinishedAt, newIngestRunId, newImportJobId, newError, version);
    }

    private void validateInvariants() {
        if ((trigger == RunTrigger.RETRY) != (retryOfRunId != null)) {
            throw new IllegalArgumentException("retryOfRunId is required for RETRY runs and rejected otherwise");
        }
        if (status == RunStatus.QUEUED && startedAt != null) {
            throw new IllegalArgumentException("startedAt must be absent for QUEUED runs");
        }
        if (status != RunStatus.QUEUED && status != RunStatus.FAILED && startedAt == null) {
            throw new IllegalArgumentException("startedAt is required for " + status + " runs");
        }
        if (status.isTerminal() != (finishedAt != null)) {
            throw new IllegalArgumentException("finishedAt must be present exactly for terminal runs");
        }
        if ((status == RunStatus.FAILED) != (error != null)) {
            throw new IllegalArgumentException("error must be present exactly for FAILED runs");
        }
        boolean importJobRequired =
                status == RunStatus.IMPORTING || status == RunStatus.SUCCEEDED || status == RunStatus.PARTIAL;
        boolean importJobForbidden = status == RunStatus.QUEUED
                || status == RunStatus.RUNNING_INGEST
                || status == RunStatus.NO_CHANGES
                || status == RunStatus.PACKED;
        if (importJobRequired && importJobId == null) {
            throw new IllegalArgumentException("importJobId is required for " + status + " runs");
        }
        if (importJobForbidden && importJobId != null) {
            throw new IllegalArgumentException("importJobId must be absent for " + status + " runs");
        }
        if (startedAt != null && startedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("startedAt must not be before createdAt");
        }
        if (finishedAt != null && finishedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("finishedAt must not be before createdAt");
        }
        if (finishedAt != null && startedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt must not be before startedAt");
        }
    }

    private static String validSeason(String season) {
        Checks.required(season, "season");
        Matcher matcher = SEASON.matcher(season);
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) != Integer.parseInt(matcher.group(1)) + 1) {
            throw new IllegalArgumentException("season must look like 2025-2026 with consecutive years: " + season);
        }
        return season;
    }

    public UUID id() {
        return id;
    }

    public PipelineSource source() {
        return source;
    }

    public String season() {
        return season;
    }

    public RunScope scope() {
        return scope;
    }

    public RunTrigger trigger() {
        return trigger;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public UUID retryOfRunId() {
        return retryOfRunId;
    }

    public RunStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
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

    public long version() {
        return version;
    }
}
