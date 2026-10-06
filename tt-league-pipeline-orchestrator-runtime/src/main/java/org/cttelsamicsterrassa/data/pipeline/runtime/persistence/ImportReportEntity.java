package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "pipeline", name = "import_report")
class ImportReportEntity {

    @Id
    @Column(name = "unit_id")
    UUID unitId;

    @Column(name = "run_id", nullable = false)
    UUID runId;

    @Column(name = "import_job_id", nullable = false)
    UUID importJobId;

    @Column(name = "import_status", nullable = false, length = 16)
    String importStatus;

    @Column(name = "files_seen", nullable = false)
    long filesSeen;

    @Column(name = "items_persisted", nullable = false)
    long itemsPersisted;

    @Column(nullable = false)
    long skipped;

    @Column(name = "processor_failures", nullable = false)
    long processorFailures;

    @Column(name = "scheduled_created", nullable = false)
    long scheduledCreated;

    @Column(name = "upgraded_to_played", nullable = false)
    long upgradedToPlayed;

    @Column(nullable = false)
    long rescheduled;

    @Column(name = "partial_actas", nullable = false)
    long partialActas;

    @Column(name = "invalid_actas", nullable = false)
    long invalidActas;

    @Column(name = "unresolved_pending_fixtures", nullable = false)
    long unresolvedPendingFixtures;

    /** Reports written before V8 read 0: the amended count was not recorded then. */
    @Column(name = "amended_played", nullable = false)
    long amendedPlayed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    String issues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_report", nullable = false)
    String rawReport;

    @Column(name = "received_at", nullable = false)
    Instant receivedAt;

    protected ImportReportEntity() {
    }

    ImportReportEntity(UUID unitId) {
        this.unitId = unitId;
    }
}
