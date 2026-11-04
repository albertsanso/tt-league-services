package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;

@Entity
@Table(schema = "pipeline", name = "match_day")
class MatchDayEntity {

    @Id
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    PipelineSource source;

    @Column(nullable = false, length = 9)
    String season;

    @Column(nullable = false)
    String competition;

    @Column(name = "group_number")
    Integer groupNumber;

    @Column
    String phase;

    @Column(nullable = false)
    int round;

    @Column(name = "first_date")
    LocalDate firstDate;

    @Column(name = "last_date")
    LocalDate lastDate;

    @Column(name = "grace_days", nullable = false)
    int graceDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    MatchDayState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", length = 16)
    CloseReason closeReason;

    @Column(name = "closed_at")
    Instant closedAt;

    @Column(name = "closed_by", length = 128)
    String closedBy;

    @Column(name = "opened_at")
    Instant openedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "last_recomputed_at", nullable = false)
    Instant lastRecomputedAt;

    /** Null until the first insert; Hibernate sets it to 0 and then increments it on every update. */
    @Version
    Long version;

    protected MatchDayEntity() {
    }

    MatchDayEntity(UUID id) {
        this.id = id;
    }
}
