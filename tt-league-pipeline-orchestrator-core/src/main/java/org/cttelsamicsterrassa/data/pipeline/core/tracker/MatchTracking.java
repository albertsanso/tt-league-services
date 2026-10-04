package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import java.time.Instant;
import java.util.UUID;

/**
 * Operational state of one platform match inside a match day. It never holds the result; the platform owns that.
 * Ignoring is a flag next to the derived status, so the status keeps following the platform.
 */
public final class MatchTracking {

    private final UUID matchId;
    private final UUID matchDayId;
    private final TrackedMatchStatus status;
    private final Instant matchDateTime;
    private final String homeTeamName;
    private final String awayTeamName;
    private final Instant firstSeenAt;
    private final Instant statusChangedAt;
    private final Instant lastSeenAt;
    private final Instant reportedAt;
    private final UUID reportedRunId;
    private final Instant ignoredAt;
    private final String ignoredBy;
    private final long version;

    private MatchTracking(
            UUID matchId,
            UUID matchDayId,
            TrackedMatchStatus status,
            Instant matchDateTime,
            String homeTeamName,
            String awayTeamName,
            Instant firstSeenAt,
            Instant statusChangedAt,
            Instant lastSeenAt,
            Instant reportedAt,
            UUID reportedRunId,
            Instant ignoredAt,
            String ignoredBy,
            long version) {
        this.matchId = TrackerChecks.required(matchId, "matchId");
        this.matchDayId = TrackerChecks.required(matchDayId, "matchDayId");
        this.status = TrackerChecks.required(status, "status");
        this.matchDateTime = matchDateTime;
        this.homeTeamName = TrackerChecks.optionalMax(homeTeamName, "homeTeamName", 255);
        this.awayTeamName = TrackerChecks.optionalMax(awayTeamName, "awayTeamName", 255);
        this.firstSeenAt = TrackerChecks.required(firstSeenAt, "firstSeenAt");
        this.statusChangedAt = TrackerChecks.required(statusChangedAt, "statusChangedAt");
        this.lastSeenAt = TrackerChecks.required(lastSeenAt, "lastSeenAt");
        this.reportedAt = reportedAt;
        this.reportedRunId = reportedRunId;
        this.ignoredAt = ignoredAt;
        this.ignoredBy = TrackerChecks.optionalMax(ignoredBy, "ignoredBy", 128);
        this.version = TrackerChecks.nonNegative(version, "version");
        if ((status == TrackedMatchStatus.REPORTED) != (reportedAt != null)) {
            throw new IllegalArgumentException("reportedAt must be set exactly when the status is REPORTED");
        }
        if (reportedAt == null && reportedRunId != null) {
            throw new IllegalArgumentException("reportedRunId requires reportedAt");
        }
        if ((ignoredAt == null) != (ignoredBy == null)) {
            throw new IllegalArgumentException("ignoredAt and ignoredBy must both be set or both be null");
        }
    }

    /**
     * A match seen for the first time. {@code reportedAt} and {@code reportedRunId} only apply when the status is
     * REPORTED; the run id may be null (periodic recompute).
     */
    public static MatchTracking first(
            UUID matchId,
            UUID matchDayId,
            TrackedMatchStatus status,
            Instant matchDateTime,
            String homeTeamName,
            String awayTeamName,
            Instant now,
            Instant reportedAt,
            UUID reportedRunId) {
        boolean reported = status == TrackedMatchStatus.REPORTED;
        return new MatchTracking(matchId, matchDayId, status, matchDateTime, homeTeamName, awayTeamName, now, now,
                now, reported ? reportedAt : null, reported ? reportedRunId : null, null, null, 0L);
    }

    /** Rebuilds a stored match. */
    public static MatchTracking restore(
            UUID matchId,
            UUID matchDayId,
            TrackedMatchStatus status,
            Instant matchDateTime,
            String homeTeamName,
            String awayTeamName,
            Instant firstSeenAt,
            Instant statusChangedAt,
            Instant lastSeenAt,
            Instant reportedAt,
            UUID reportedRunId,
            Instant ignoredAt,
            String ignoredBy,
            long version) {
        return new MatchTracking(matchId, matchDayId, status, matchDateTime, homeTeamName, awayTeamName, firstSeenAt,
                statusChangedAt, lastSeenAt, reportedAt, reportedRunId, ignoredAt, ignoredBy, version);
    }

    /**
     * Applies a fresh platform reading. The reported-at pair is set when the match first becomes REPORTED, kept while
     * it stays REPORTED and cleared when it leaves REPORTED.
     */
    public MatchTracking observe(
            TrackedMatchStatus newStatus,
            Instant newMatchDateTime,
            String newHomeTeamName,
            String newAwayTeamName,
            Instant now,
            Instant newReportedAt,
            UUID newReportedRunId) {
        Instant reported = null;
        UUID reportedRun = null;
        if (newStatus == TrackedMatchStatus.REPORTED) {
            boolean stays = status == TrackedMatchStatus.REPORTED;
            reported = stays ? reportedAt : newReportedAt;
            reportedRun = stays ? reportedRunId : newReportedRunId;
        }
        return new MatchTracking(matchId, matchDayId, newStatus, newMatchDateTime, newHomeTeamName, newAwayTeamName,
                firstSeenAt, newStatus == status ? statusChangedAt : now, now, reported, reportedRun, ignoredAt,
                ignoredBy, version);
    }

    public MatchTracking ignore(String actor, Instant now) {
        if (isIgnored()) {
            throw new IllegalMatchDayTransitionException(matchDayId, "match " + matchId + " is already ignored");
        }
        return new MatchTracking(matchId, matchDayId, status, matchDateTime, homeTeamName, awayTeamName, firstSeenAt,
                statusChangedAt, lastSeenAt, reportedAt, reportedRunId, now, actor, version);
    }

    public MatchTracking unignore() {
        if (!isIgnored()) {
            throw new IllegalMatchDayTransitionException(matchDayId, "match " + matchId + " is not ignored");
        }
        return new MatchTracking(matchId, matchDayId, status, matchDateTime, homeTeamName, awayTeamName, firstSeenAt,
                statusChangedAt, lastSeenAt, reportedAt, reportedRunId, null, null, version);
    }

    public MatchTracking moveTo(UUID newMatchDayId) {
        return new MatchTracking(matchId, newMatchDayId, status, matchDateTime, homeTeamName, awayTeamName,
                firstSeenAt, statusChangedAt, lastSeenAt, reportedAt, reportedRunId, ignoredAt, ignoredBy, version);
    }

    public boolean isIgnored() {
        return ignoredAt != null;
    }

    /** Reported, postponed or ignored: nothing left to wait for inside the match day. */
    public boolean isResolved() {
        return status == TrackedMatchStatus.REPORTED || status == TrackedMatchStatus.POSTPONED || isIgnored();
    }

    public UUID matchId() {
        return matchId;
    }

    public UUID matchDayId() {
        return matchDayId;
    }

    public TrackedMatchStatus status() {
        return status;
    }

    public Instant matchDateTime() {
        return matchDateTime;
    }

    public String homeTeamName() {
        return homeTeamName;
    }

    public String awayTeamName() {
        return awayTeamName;
    }

    public Instant firstSeenAt() {
        return firstSeenAt;
    }

    public Instant statusChangedAt() {
        return statusChangedAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public Instant reportedAt() {
        return reportedAt;
    }

    public UUID reportedRunId() {
        return reportedRunId;
    }

    public Instant ignoredAt() {
        return ignoredAt;
    }

    public String ignoredBy() {
        return ignoredBy;
    }

    public long version() {
        return version;
    }
}
