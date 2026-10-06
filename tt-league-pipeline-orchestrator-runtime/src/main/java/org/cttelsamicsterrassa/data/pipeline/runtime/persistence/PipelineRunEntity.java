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
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "pipeline", name = "pipeline_run")
class PipelineRunEntity {

    @Id
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    PipelineSource source;

    @Column(nullable = false, length = 9)
    String season;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String scope;

    @Column(nullable = false)
    boolean force;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, length = 16)
    RunTrigger trigger;

    @Column(name = "requested_by", nullable = false, length = 128)
    String requestedBy;

    @Column(name = "retry_of_run_id")
    UUID retryOfRunId;

    @Column(name = "retry_of_unit_id")
    UUID retryOfUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    RunStatus status;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    // pipeline_run.ingest_run_id and import_job_id are legacy columns (V10): the units carry them, nothing maps them.

    @Column(name = "error_code", length = 64)
    String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    String errorMessage;

    /** Null until the first insert; Hibernate sets it to 0 and then increments it on every update. */
    @Version
    Long version;

    protected PipelineRunEntity() {
    }

    PipelineRunEntity(UUID id) {
        this.id = id;
    }
}
