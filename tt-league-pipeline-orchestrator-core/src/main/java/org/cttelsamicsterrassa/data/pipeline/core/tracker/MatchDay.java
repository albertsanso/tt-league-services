package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.time.Instant;
import java.util.UUID;

/** Immutable match-day aggregate; every transition returns a new instance. */
public final class MatchDay {

    private final UUID id;
    private final MatchDayKey key;
    private final MatchDayWindow window;
    private final MatchDayState state;
    private final CloseReason closeReason;
    private final Instant closedAt;
    private final String closedBy;
    private final Instant openedAt;
    private final Instant createdAt;
    private final Instant lastRecomputedAt;
    private final long version;

    private MatchDay(
            UUID id,
            MatchDayKey key,
            MatchDayWindow window,
            MatchDayState state,
            CloseReason closeReason,
            Instant closedAt,
            String closedBy,
            Instant openedAt,
            Instant createdAt,
            Instant lastRecomputedAt,
            long version) {
        this.id = TrackerChecks.required(id, "id");
        this.key = TrackerChecks.required(key, "key");
        this.window = TrackerChecks.required(window, "window");
        this.state = TrackerChecks.required(state, "state");
        this.closeReason = closeReason;
        this.closedAt = closedAt;
        this.closedBy = TrackerChecks.optionalMax(closedBy, "closedBy", 128);
        this.openedAt = openedAt;
        this.createdAt = TrackerChecks.required(createdAt, "createdAt");
        this.lastRecomputedAt = TrackerChecks.required(lastRecomputedAt, "lastRecomputedAt");
        this.version = TrackerChecks.nonNegative(version, "version");
        boolean closedFields = closeReason != null && closedAt != null && closedBy != null;
        boolean noClosedFields = closeReason == null && closedAt == null && closedBy == null;
        if (state == MatchDayState.CLOSED ? !closedFields : !noClosedFields) {
            throw new IllegalArgumentException(
                    "closeReason, closedAt and closedBy must be set exactly when the state is CLOSED");
        }
        if (state == MatchDayState.OPEN && openedAt == null) {
            throw new IllegalArgumentException("openedAt is required for an OPEN match day");
        }
    }

    /** A new match day, UPCOMING and not yet persisted. */
    public static MatchDay create(UUID id, MatchDayKey key, MatchDayWindow window, Instant now) {
        return new MatchDay(id, key, window, MatchDayState.UPCOMING, null, null, null, null, now, now, 0L);
    }

    /** Rebuilds a stored match day. */
    public static MatchDay restore(
            UUID id,
            MatchDayKey key,
            MatchDayWindow window,
            MatchDayState state,
            CloseReason closeReason,
            Instant closedAt,
            String closedBy,
            Instant openedAt,
            Instant createdAt,
            Instant lastRecomputedAt,
            long version) {
        return new MatchDay(
                id, key, window, state, closeReason, closedAt, closedBy, openedAt, createdAt, lastRecomputedAt,
                version);
    }

    public MatchDay open(Instant now) {
        if (state != MatchDayState.UPCOMING) {
            throw illegal("cannot open from " + state);
        }
        return new MatchDay(id, key, window, MatchDayState.OPEN, null, null, null, now, createdAt, lastRecomputedAt,
                version);
    }

    public MatchDay close(CloseReason reason, String actor, Instant now) {
        if (state == MatchDayState.CLOSED) {
            throw illegal("is already closed");
        }
        return new MatchDay(id, key, window, MatchDayState.CLOSED, TrackerChecks.required(reason, "reason"), now,
                actor, openedAt, createdAt, lastRecomputedAt, version);
    }

    public MatchDay reopen(Instant now) {
        if (state != MatchDayState.CLOSED) {
            throw illegal("cannot reopen from " + state);
        }
        return new MatchDay(id, key, window, MatchDayState.OPEN, null, null, null,
                openedAt != null ? openedAt : now, createdAt, lastRecomputedAt, version);
    }

    public MatchDay withWindow(MatchDayWindow newWindow) {
        return new MatchDay(id, key, newWindow, state, closeReason, closedAt, closedBy, openedAt, createdAt,
                lastRecomputedAt, version);
    }

    public MatchDay recomputed(Instant now) {
        return new MatchDay(id, key, window, state, closeReason, closedAt, closedBy, openedAt, createdAt, now,
                version);
    }

    private IllegalMatchDayTransitionException illegal(String message) {
        return new IllegalMatchDayTransitionException(id, message);
    }

    public UUID id() {
        return id;
    }

    public MatchDayKey key() {
        return key;
    }

    public MatchDayWindow window() {
        return window;
    }

    public MatchDayState state() {
        return state;
    }

    public CloseReason closeReason() {
        return closeReason;
    }

    public Instant closedAt() {
        return closedAt;
    }

    public String closedBy() {
        return closedBy;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastRecomputedAt() {
        return lastRecomputedAt;
    }

    public long version() {
        return version;
    }
}
