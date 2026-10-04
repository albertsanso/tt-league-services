package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackedMatchStatus;

/** The platform match id is a plain reference: no relation to any platform table. */
@Entity
@Table(schema = "pipeline", name = "match_tracking")
class MatchTrackingEntity {

    @Id
    @Column(name = "match_id")
    UUID matchId;

    @Column(name = "match_day_id", nullable = false)
    UUID matchDayId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    TrackedMatchStatus status;

    @Column(name = "match_date_time")
    Instant matchDateTime;

    @Column(name = "home_team_name")
    String homeTeamName;

    @Column(name = "away_team_name")
    String awayTeamName;

    @Column(name = "first_seen_at", nullable = false)
    Instant firstSeenAt;

    @Column(name = "status_changed_at", nullable = false)
    Instant statusChangedAt;

    @Column(name = "last_seen_at", nullable = false)
    Instant lastSeenAt;

    @Column(name = "reported_at")
    Instant reportedAt;

    @Column(name = "reported_run_id")
    UUID reportedRunId;

    @Column(name = "ignored_at")
    Instant ignoredAt;

    @Column(name = "ignored_by", length = 128)
    String ignoredBy;

    /** Null until the first insert; Hibernate sets it to 0 and then increments it on every update. */
    @Version
    Long version;

    protected MatchTrackingEntity() {
    }

    MatchTrackingEntity(UUID matchId) {
        this.matchId = matchId;
    }
}
