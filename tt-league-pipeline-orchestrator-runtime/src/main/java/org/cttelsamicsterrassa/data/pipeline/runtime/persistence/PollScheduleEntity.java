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
import org.cttelsamicsterrassa.data.pipeline.core.polling.PolicyLevel;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "pipeline", name = "poll_schedule")
class PollScheduleEntity {

    @Id
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    PipelineSource source;

    @Column(nullable = false, length = 9)
    String season;

    @Column(name = "scope_key", nullable = false, length = 64)
    String scopeKey;

    /** {@code FULL_REFRESH} or {@code GROUP}; derived from the filter, kept as a column for readable queries. */
    @Column(nullable = false, length = 16)
    String kind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    String filter;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_level", nullable = false, length = 16)
    PolicyLevel policyLevel;

    @Column(name = "interval_seconds")
    Long intervalSeconds;

    @Column(name = "consecutive_no_change", nullable = false)
    int consecutiveNoChange;

    @Column(name = "next_run_at")
    Instant nextRunAt;

    @Column(name = "last_run_at")
    Instant lastRunAt;

    @Column(name = "pending_run_id")
    UUID pendingRunId;

    @Column(name = "stopped_at")
    Instant stoppedAt;

    @Column(name = "stop_reason", length = 32)
    String stopReason;

    @Column(name = "alerted_at")
    Instant alertedAt;

    /** Null until the first insert; Hibernate sets it to 0 and then increments it on every update. */
    @Version
    Long version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected PollScheduleEntity() {
    }

    PollScheduleEntity(UUID id) {
        this.id = id;
    }
}
