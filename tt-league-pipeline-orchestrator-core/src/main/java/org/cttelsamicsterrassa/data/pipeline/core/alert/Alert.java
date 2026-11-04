package org.cttelsamicsterrassa.data.pipeline.core.alert;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import java.time.Instant;
import java.util.UUID;

/** Immutable alert aggregate; every transition returns a new instance. */
public final class Alert {

    private final UUID id;
    private final AlertKind kind;
    private final String conditionKey;
    private final PipelineSource source;
    private final String season;
    private final String title;
    private final String detail;
    private final Instant raisedAt;
    private final Instant notifiedAt;
    private final int notifyAttempts;
    private final String lastFailure;
    private final Instant clearedAt;
    private final long version;

    private Alert(
            UUID id,
            AlertKind kind,
            String conditionKey,
            PipelineSource source,
            String season,
            String title,
            String detail,
            Instant raisedAt,
            Instant notifiedAt,
            int notifyAttempts,
            String lastFailure,
            Instant clearedAt,
            long version) {
        this.id = AlertChecks.required(id, "id");
        this.kind = AlertChecks.required(kind, "kind");
        this.conditionKey = AlertChecks.nonBlankMax(conditionKey, "conditionKey", 255);
        this.source = source;
        this.season = AlertChecks.optionalMax(season, "season", 16);
        this.title = AlertChecks.nonBlankMax(title, "title", 255);
        this.detail = AlertChecks.required(detail, "detail");
        this.raisedAt = AlertChecks.required(raisedAt, "raisedAt");
        this.notifiedAt = notifiedAt;
        this.notifyAttempts = (int) AlertChecks.nonNegative(notifyAttempts, "notifyAttempts");
        this.lastFailure = AlertChecks.optionalMax(lastFailure, "lastFailure", 128);
        this.clearedAt = clearedAt;
        this.version = AlertChecks.nonNegative(version, "version");
    }

    /** A new alert for a holding condition, not yet persisted and not yet notified. */
    public static Alert raise(UUID id, AlertCondition condition, Instant now) {
        AlertChecks.required(condition, "condition");
        return new Alert(id, condition.kind(), condition.conditionKey(), condition.source(), condition.season(),
                condition.title(), condition.detail(), now, null, 0, null, null, 0L);
    }

    /** Rebuilds a stored alert. */
    public static Alert restore(
            UUID id,
            AlertKind kind,
            String conditionKey,
            PipelineSource source,
            String season,
            String title,
            String detail,
            Instant raisedAt,
            Instant notifiedAt,
            int notifyAttempts,
            String lastFailure,
            Instant clearedAt,
            long version) {
        return new Alert(id, kind, conditionKey, source, season, title, detail, raisedAt, notifiedAt, notifyAttempts,
                lastFailure, clearedAt, version);
    }

    /** The notification went out; the failure of an earlier attempt is forgotten. */
    public Alert notified(Instant now) {
        requireActive("notified");
        AlertChecks.required(now, "now");
        return new Alert(id, kind, conditionKey, source, season, title, detail, raisedAt, now, notifyAttempts + 1,
                null, null, version);
    }

    /** A send attempt failed; {@code reason} is an exception simple name, never a message. */
    public Alert notifyFailed(String reason) {
        requireActive("notifyFailed");
        return new Alert(id, kind, conditionKey, source, season, title, detail, raisedAt, notifiedAt,
                notifyAttempts + 1, AlertChecks.nonBlankMax(reason, "reason", 128), null, version);
    }

    public Alert cleared(Instant now) {
        requireActive("cleared");
        AlertChecks.required(now, "now");
        return new Alert(id, kind, conditionKey, source, season, title, detail, raisedAt, notifiedAt, notifyAttempts,
                lastFailure, now, version);
    }

    private void requireActive(String operation) {
        if (clearedAt != null) {
            throw new IllegalStateException("Alert " + id + " is already cleared; cannot apply " + operation);
        }
    }

    public boolean isActive() {
        return clearedAt == null;
    }

    public boolean isNotified() {
        return notifiedAt != null;
    }

    public UUID id() {
        return id;
    }

    public AlertKind kind() {
        return kind;
    }

    public String conditionKey() {
        return conditionKey;
    }

    public PipelineSource source() {
        return source;
    }

    public String season() {
        return season;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return detail;
    }

    public Instant raisedAt() {
        return raisedAt;
    }

    public Instant notifiedAt() {
        return notifiedAt;
    }

    public int notifyAttempts() {
        return notifyAttempts;
    }

    public String lastFailure() {
        return lastFailure;
    }

    public Instant clearedAt() {
        return clearedAt;
    }

    public long version() {
        return version;
    }
}
