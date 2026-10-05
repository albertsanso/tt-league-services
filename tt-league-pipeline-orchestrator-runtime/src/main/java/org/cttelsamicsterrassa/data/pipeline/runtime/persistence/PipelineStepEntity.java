package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;

@Entity
@Table(schema = "pipeline", name = "pipeline_step")
class PipelineStepEntity {

    @Id
    UUID id;

    @Column(name = "run_id", nullable = false)
    UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    StepKind kind;

    @Column(nullable = false)
    int attempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    StepStatus status;

    @Column(name = "started_at", nullable = false)
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Column(name = "external_ref", length = 64)
    String externalRef;

    @Column(length = 32)
    String outcome;

    Boolean retryable;

    @Column(name = "error_code", length = 64)
    String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    String errorMessage;

    @Column(name = "log_ref", length = 512)
    String logRef;

    /** The three health columns are all null or all set (a CHECK in V8); only INGEST steps carry them. */
    @Column(name = "http_errors")
    Long httpErrors;

    @Column(name = "timeouts")
    Long timeouts;

    @Column(name = "parse_errors")
    Long parseErrors;

    protected PipelineStepEntity() {
    }

    PipelineStepEntity(UUID id) {
        this.id = id;
    }
}
