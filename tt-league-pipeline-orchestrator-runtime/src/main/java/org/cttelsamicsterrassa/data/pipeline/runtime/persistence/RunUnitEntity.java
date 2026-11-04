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
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "pipeline", name = "pipeline_unit")
class RunUnitEntity {

    @Id
    UUID id;

    @Column(name = "run_id", nullable = false)
    UUID runId;

    @Column(nullable = false)
    int ordinal;

    @Column(name = "unit_key", nullable = false, length = 64)
    String unitKey;

    @Column(nullable = false, length = 256)
    String label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UnitStatus status;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "finished_at")
    Instant finishedAt;

    @Column(name = "ingest_run_id", length = 64)
    String ingestRunId;

    @Column(name = "import_job_id")
    UUID importJobId;

    @Column(name = "error_code", length = 64)
    String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    String errorMessage;

    /** The progress columns are all null (no progress) or consistent with each other (a CHECK in V10). */
    @Enumerated(EnumType.STRING)
    @Column(name = "progress_step", length = 16)
    StepKind progressStep;

    @Column(name = "progress_stage", length = 64)
    String progressStage;

    @Column(name = "progress_items")
    Long progressItems;

    @Column(name = "progress_total")
    Long progressTotal;

    @Column(name = "progress_current", length = 256)
    String progressCurrent;

    @Column(name = "progress_updated_at")
    Instant progressUpdatedAt;

    /** Null until the first insert; Hibernate sets it to 0 and then increments it on every update. */
    @Version
    Long version;

    protected RunUnitEntity() {
    }

    RunUnitEntity(UUID id) {
        this.id = id;
    }
}
