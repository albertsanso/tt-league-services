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
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

@Entity
@Table(schema = "pipeline", name = "alert")
class AlertEntity {

    @Id
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    AlertKind kind;

    @Column(name = "condition_key", nullable = false, length = 255)
    String conditionKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    PipelineSource source;

    @Column(length = 16)
    String season;

    @Column(nullable = false, length = 255)
    String title;

    @Column(nullable = false, columnDefinition = "text")
    String detail;

    @Column(name = "raised_at", nullable = false)
    Instant raisedAt;

    @Column(name = "notified_at")
    Instant notifiedAt;

    @Column(name = "notify_attempts", nullable = false)
    int notifyAttempts;

    @Column(name = "last_failure", length = 128)
    String lastFailure;

    @Column(name = "cleared_at")
    Instant clearedAt;

    /** Null until the first insert; Hibernate sets it to 0 and then increments it on every update. */
    @Version
    Long version;

    protected AlertEntity() {
    }

    AlertEntity(UUID id) {
        this.id = id;
    }
}
