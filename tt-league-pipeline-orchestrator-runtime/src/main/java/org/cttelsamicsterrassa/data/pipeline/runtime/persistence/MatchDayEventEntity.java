package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayEventKind;
import org.springframework.data.domain.Persistable;

/** Append-only; the match id has no foreign key so it survives the removal of the match. */
@Entity
@Table(schema = "pipeline", name = "match_day_event")
class MatchDayEventEntity implements Persistable<UUID> {

    @Id
    UUID id;

    @Column(name = "match_day_id", nullable = false)
    UUID matchDayId;

    @Column(name = "match_id")
    UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    MatchDayEventKind kind;

    @Column(nullable = false, length = 128)
    String actor;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    @Column(name = "run_id")
    UUID runId;

    @Column(length = 2000)
    String note;

    @Transient
    private boolean isNew = true;

    protected MatchDayEventEntity() {
    }

    MatchDayEventEntity(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
