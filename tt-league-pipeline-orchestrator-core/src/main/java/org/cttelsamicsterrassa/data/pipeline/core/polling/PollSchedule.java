package org.cttelsamicsterrassa.data.pipeline.core.polling;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;

/**
 * Immutable poll schedule of one {@code (source, season, scopeKey)}: either the per-source {@code FULL_REFRESH} unit
 * (no filter) or one ingest group. Every change returns a new instance; {@code decided} returns {@code this} when the
 * decision changes nothing.
 */
public final class PollSchedule {

    public static final String FULL_REFRESH_KEY = "FULL_REFRESH";

    private static final PollingPolicy POLICY = new PollingPolicy();

    private final UUID id;
    private final PipelineSource source;
    private final String season;
    private final String scopeKey;
    private final ScopeFilter filter;
    private final PolicyLevel level;
    private final Duration interval;
    private final int consecutiveNoChange;
    private final Instant nextRunAt;
    private final Instant lastRunAt;
    private final UUID pendingRunId;
    private final Instant stoppedAt;
    private final String stopReason;
    private final Instant alertedAt;
    private final long version;

    private PollSchedule(
            UUID id,
            PipelineSource source,
            String season,
            String scopeKey,
            ScopeFilter filter,
            PolicyLevel level,
            Duration interval,
            int consecutiveNoChange,
            Instant nextRunAt,
            Instant lastRunAt,
            UUID pendingRunId,
            Instant stoppedAt,
            String stopReason,
            Instant alertedAt,
            long version) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.source = Objects.requireNonNull(source, "source is required");
        this.season = PipelineRun.requireValidSeason(season);
        this.scopeKey = Objects.requireNonNull(scopeKey, "scopeKey is required");
        if (scopeKey.isBlank() || scopeKey.length() > 64) {
            throw new IllegalArgumentException("scopeKey must be 1 to 64 characters");
        }
        this.filter = filter;
        this.level = Objects.requireNonNull(level, "level is required");
        boolean full = FULL_REFRESH_KEY.equals(scopeKey);
        if (full != (filter == null)) {
            throw new IllegalArgumentException("filter must be null exactly for the FULL_REFRESH unit");
        }
        if (full != (level == PolicyLevel.FULL_REFRESH)) {
            throw new IllegalArgumentException("level FULL_REFRESH is reserved for the FULL_REFRESH unit");
        }
        this.interval = interval;
        if (interval != null && (interval.isZero() || interval.isNegative())) {
            throw new IllegalArgumentException("interval must be positive");
        }
        if (consecutiveNoChange < 0 || version < 0) {
            throw new IllegalArgumentException("consecutiveNoChange and version must not be negative");
        }
        this.consecutiveNoChange = consecutiveNoChange;
        this.nextRunAt = nextRunAt;
        this.lastRunAt = lastRunAt;
        this.pendingRunId = pendingRunId;
        if ((stoppedAt == null) != (stopReason == null)) {
            throw new IllegalArgumentException("stoppedAt and stopReason must both be set or both be null");
        }
        if ((level == PolicyLevel.STOPPED) != (stoppedAt != null)) {
            throw new IllegalArgumentException("stoppedAt must be set exactly when the level is STOPPED");
        }
        this.stoppedAt = stoppedAt;
        this.stopReason = stopReason;
        this.alertedAt = alertedAt;
        this.version = version;
    }

    /** A new group unit, version 0, built from its first decision. */
    public static PollSchedule group(
            UUID id, PipelineSource source, String season, String scopeKey, ScopeFilter filter, PollDecision decision,
            Instant now) {
        Objects.requireNonNull(filter, "filter is required");
        return blank(id, source, season, scopeKey, filter, decision.level()).decided(decision, now);
    }

    /** The new {@code FULL_REFRESH} unit, version 0. */
    public static PollSchedule fullRefresh(UUID id, PipelineSource source, String season, PollDecision decision) {
        return blank(id, source, season, FULL_REFRESH_KEY, null, PolicyLevel.FULL_REFRESH).decided(decision, null);
    }

    private static PollSchedule blank(
            UUID id, PipelineSource source, String season, String scopeKey, ScopeFilter filter, PolicyLevel level) {
        PolicyLevel initial = level == PolicyLevel.STOPPED ? PolicyLevel.OVERDUE : level;
        return new PollSchedule(id, source, season, scopeKey, filter, initial, Duration.ofSeconds(1), 0, null, null,
                null, null, null, null, 0L);
    }

    /** Rebuilds a stored schedule. */
    public static PollSchedule restore(
            UUID id,
            PipelineSource source,
            String season,
            String scopeKey,
            ScopeFilter filter,
            PolicyLevel level,
            Duration interval,
            int consecutiveNoChange,
            Instant nextRunAt,
            Instant lastRunAt,
            UUID pendingRunId,
            Instant stoppedAt,
            String stopReason,
            Instant alertedAt,
            long version) {
        return new PollSchedule(id, source, season, scopeKey, filter, level, interval, consecutiveNoChange, nextRunAt,
                lastRunAt, pendingRunId, stoppedAt, stopReason, alertedAt, version);
    }

    public PollState state() {
        return new PollState(level, consecutiveNoChange, lastRunAt);
    }

    /** Applies a policy decision; a {@code STOPPED} decision records the stop once, any other one clears it. */
    public PollSchedule decided(PollDecision decision, Instant now) {
        Instant newStoppedAt = null;
        String newStopReason = null;
        Instant newAlertedAt = alertedAt;
        if (decision.level() == PolicyLevel.STOPPED) {
            boolean already = level == PolicyLevel.STOPPED;
            newStoppedAt = already ? stoppedAt : now;
            newStopReason = already ? stopReason : decision.stopReason();
            if (newStoppedAt == null) {
                throw new IllegalArgumentException("A STOPPED decision needs the decision time");
            }
        } else if (level == PolicyLevel.STOPPED) {
            newAlertedAt = null;
        }
        if (decision.level() == level && Objects.equals(decision.effectiveInterval(), interval)
                && decision.consecutiveNoChange() == consecutiveNoChange
                && Objects.equals(decision.nextRunAt(), nextRunAt) && Objects.equals(newStoppedAt, stoppedAt)
                && Objects.equals(newAlertedAt, alertedAt)) {
            return this;
        }
        return new PollSchedule(id, source, season, scopeKey, filter, decision.level(), decision.effectiveInterval(),
                decision.consecutiveNoChange(), decision.nextRunAt(), lastRunAt, pendingRunId, newStoppedAt,
                newStopReason, newAlertedAt, version);
    }

    /** The unit's filter changed because rounds opened or closed. */
    public PollSchedule withFilter(ScopeFilter newFilter) {
        Objects.requireNonNull(newFilter, "filter is required");
        if (newFilter.equals(filter)) {
            return this;
        }
        return new PollSchedule(id, source, season, scopeKey, newFilter, level, interval, consecutiveNoChange,
                nextRunAt, lastRunAt, pendingRunId, stoppedAt, stopReason, alertedAt, version);
    }

    public PollSchedule launched(UUID runId, Instant at) {
        Objects.requireNonNull(runId, "runId is required");
        Objects.requireNonNull(at, "at is required");
        if (pendingRunId != null) {
            throw new IllegalStateException("Schedule " + id + " already has a pending run " + pendingRunId);
        }
        return new PollSchedule(id, source, season, scopeKey, filter, level, interval, consecutiveNoChange, nextRunAt,
                lastRunAt, runId, stoppedAt, stopReason, alertedAt, version);
    }

    /** The pending run reached {@code terminal}: counter rules apply and the run ends the polling interval at {@code at}. */
    public PollSchedule outcome(RunStatus terminal, Instant at) {
        Objects.requireNonNull(at, "at is required");
        PollState next = POLICY.applyOutcome(state(), terminal);
        return new PollSchedule(id, source, season, scopeKey, filter, level, interval, next.consecutiveNoChange(),
                nextRunAt, at, null, stoppedAt, stopReason, alertedAt, version);
    }

    /** The pending run no longer exists. */
    public PollSchedule pendingCleared() {
        return new PollSchedule(id, source, season, scopeKey, filter, level, interval, consecutiveNoChange, nextRunAt,
                lastRunAt, null, stoppedAt, stopReason, alertedAt, version);
    }

    public PollSchedule alerted(Instant at) {
        Objects.requireNonNull(at, "at is required");
        return new PollSchedule(id, source, season, scopeKey, filter, level, interval, consecutiveNoChange, nextRunAt,
                lastRunAt, pendingRunId, stoppedAt, stopReason, at, version);
    }

    /**
     * Clears the stop and makes the unit due now. The last run is forgotten so the policy polls the unit once before it
     * can stop again (and alert again).
     */
    public PollSchedule resumed(Instant now) {
        if (level != PolicyLevel.STOPPED) {
            throw new IllegalStateException("Schedule " + id + " is not stopped");
        }
        return new PollSchedule(id, source, season, scopeKey, filter, PolicyLevel.OVERDUE, Duration.ofHours(24), 0,
                Objects.requireNonNull(now, "now is required"), null, pendingRunId, null, null, null, version);
    }

    public boolean isFullRefresh() {
        return filter == null;
    }

    public boolean isStopped() {
        return level == PolicyLevel.STOPPED;
    }

    /** Due: not stopped, nothing pending and the next run time has come. */
    public boolean isDue(Instant now) {
        return !isStopped() && pendingRunId == null && nextRunAt != null && !nextRunAt.isAfter(now);
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

    public String scopeKey() {
        return scopeKey;
    }

    public ScopeFilter filter() {
        return filter;
    }

    public PolicyLevel level() {
        return level;
    }

    public Duration interval() {
        return interval;
    }

    public int consecutiveNoChange() {
        return consecutiveNoChange;
    }

    public Instant nextRunAt() {
        return nextRunAt;
    }

    public Instant lastRunAt() {
        return lastRunAt;
    }

    public UUID pendingRunId() {
        return pendingRunId;
    }

    public Instant stoppedAt() {
        return stoppedAt;
    }

    public String stopReason() {
        return stopReason;
    }

    public Instant alertedAt() {
        return alertedAt;
    }

    public long version() {
        return version;
    }
}
