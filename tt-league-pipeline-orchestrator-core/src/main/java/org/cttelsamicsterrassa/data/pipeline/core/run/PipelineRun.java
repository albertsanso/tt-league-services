package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable run aggregate; every transition returns a new instance. A run only knows whether it is waiting, running
 * or finished: the work happens in its {@link RunUnit}s and {@link RunOutcomeRules} derives the final status.
 */
public final class PipelineRun {

    private static final Pattern SEASON = Pattern.compile("(\\d{4})-(\\d{4})");

    private final UUID id;
    private final PipelineSource source;
    private final String season;
    private final RunScope scope;
    private final boolean force;
    private final RunTrigger trigger;
    private final String requestedBy;
    private final UUID retryOfRunId;
    private final UUID retryOfUnitId;
    private final RunStatus status;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final RunError error;
    private final long version;

    private PipelineRun(
            UUID id,
            PipelineSource source,
            String season,
            RunScope scope,
            boolean force,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId,
            UUID retryOfUnitId,
            RunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            RunError error,
            long version) {
        this.id = Checks.required(id, "id");
        this.source = Checks.required(source, "source");
        this.season = requireValidSeason(season);
        this.scope = Checks.required(scope, "scope");
        this.force = force;
        this.trigger = Checks.required(trigger, "trigger");
        this.requestedBy = Checks.nonBlankMax(requestedBy, "requestedBy", 128);
        this.retryOfRunId = retryOfRunId;
        this.retryOfUnitId = retryOfUnitId;
        this.status = Checks.required(status, "status");
        this.createdAt = Checks.required(createdAt, "createdAt");
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.error = error;
        this.version = Checks.nonNegative(version, "version");
        validateInvariants();
    }

    /** Queues a run; {@code retryOfRunId} is set exactly for RETRY runs. */
    public static PipelineRun queue(
            UUID id,
            PipelineSource source,
            String season,
            RunScope scope,
            boolean force,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId,
            Instant now) {
        return queue(id, source, season, scope, force, trigger, requestedBy, retryOfRunId, null, now);
    }

    /** Queues a run; {@code retryOfUnitId} is set exactly for UNIT_RETRY runs, which also carry {@code retryOfRunId}. */
    public static PipelineRun queue(
            UUID id,
            PipelineSource source,
            String season,
            RunScope scope,
            boolean force,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId,
            UUID retryOfUnitId,
            Instant now) {
        return new PipelineRun(id, source, season, scope, force, trigger, requestedBy, retryOfRunId, retryOfUnitId,
                RunStatus.QUEUED, now, null, null, null, 0L);
    }

    /** Rebuilds a stored run; used by adapters only. Re-validates every invariant. */
    public static PipelineRun restore(
            UUID id,
            PipelineSource source,
            String season,
            RunScope scope,
            boolean force,
            RunTrigger trigger,
            String requestedBy,
            UUID retryOfRunId,
            UUID retryOfUnitId,
            RunStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            RunError error,
            long version) {
        return new PipelineRun(id, source, season, scope, force, trigger, requestedBy, retryOfRunId, retryOfUnitId,
                status, createdAt, startedAt, finishedAt, error, version);
    }

    /** QUEUED to RUNNING: the run's units have been planned and the executor takes over. */
    public PipelineRun start(Instant at) {
        return move(RunStatus.RUNNING, at, at, null, null);
    }

    /**
     * RUNNING to the terminal status derived from the units by {@link RunOutcomeRules}; the error is required exactly
     * for FAILED.
     */
    public PipelineRun finish(RunStatus derived, RunError errorOrNull, Instant at) {
        Checks.required(derived, "derived");
        if (!derived.isTerminal()) {
            throw new IllegalArgumentException("A run can only finish with a terminal status: " + derived);
        }
        if ((derived == RunStatus.FAILED) != (errorOrNull != null)) {
            throw new IllegalArgumentException("error must be present exactly for FAILED runs");
        }
        return move(derived, at, startedAt, at, errorOrNull);
    }

    /** Fails a queued or running run outright; a finished set of units goes through {@link #finish}. */
    public PipelineRun fail(RunError error, Instant at) {
        Checks.required(error, "error");
        return move(RunStatus.FAILED, at, startedAt, at, error);
    }

    private PipelineRun move(
            RunStatus next, Instant at, Instant newStartedAt, Instant newFinishedAt, RunError newError) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalRunTransitionException(id, status, next);
        }
        Checks.required(at, "at");
        return new PipelineRun(id, source, season, scope, force, trigger, requestedBy, retryOfRunId, retryOfUnitId,
                next, createdAt, newStartedAt, newFinishedAt, newError, version);
    }

    private void validateInvariants() {
        boolean retry = trigger == RunTrigger.RETRY || trigger == RunTrigger.UNIT_RETRY;
        if (retry != (retryOfRunId != null)) {
            throw new IllegalArgumentException(
                    "retryOfRunId is required for RETRY and UNIT_RETRY runs and rejected otherwise");
        }
        if ((trigger == RunTrigger.UNIT_RETRY) != (retryOfUnitId != null)) {
            throw new IllegalArgumentException("retryOfUnitId is required for UNIT_RETRY runs and rejected otherwise");
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

    /** Validates the {@code 2025-2026} season shape (consecutive years) shared by runs and triggers. */
    public static String requireValidSeason(String season) {
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

    public boolean force() {
        return force;
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

    /** The unit a UNIT_RETRY run re-runs; null for every other trigger. */
    public UUID retryOfUnitId() {
        return retryOfUnitId;
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

    public RunError error() {
        return error;
    }

    public long version() {
        return version;
    }
}
