package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "pipeline", name = "poll_policy")
class PollPolicyEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    PipelineSource source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String settings;

    /** Null until the first insert; Hibernate sets it to 0 and increments it on every update. */
    @Version
    Long version;

    @Column(name = "updated_by", nullable = false, length = 128)
    String updatedBy;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected PollPolicyEntity() {
    }

    PollPolicyEntity(PipelineSource source) {
        this.source = source;
    }
}
