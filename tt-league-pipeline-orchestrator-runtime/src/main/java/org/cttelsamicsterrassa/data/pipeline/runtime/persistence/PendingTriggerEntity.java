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
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScopeType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

@Entity
@Table(schema = "pipeline", name = "pending_trigger")
class PendingTriggerEntity implements Persistable<PipelineSource> {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    PipelineSource source;

    @Column(nullable = false, length = 9)
    String season;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    ScopeType scopeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String filters;

    @Column(nullable = false)
    boolean force;

    @Column(name = "requested_by", nullable = false, length = 128)
    String requestedBy;

    @Column(name = "requested_at", nullable = false)
    Instant requestedAt;

    @Transient
    private boolean isNew = true;

    protected PendingTriggerEntity() {
    }

    PendingTriggerEntity(PipelineSource source) {
        this.source = source;
    }

    @Override
    public PipelineSource getId() {
        return source;
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
