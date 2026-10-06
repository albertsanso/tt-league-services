package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The V9 migration: {@code run_artifact.purged_at} and {@code pipeline_step.import_job_reused}. */
class ReplayRetentionMigrationTest extends AbstractPersistenceTest {

    private static final String SHA = "0123456789abcdef".repeat(4);

    @Autowired
    JdbcTemplate template;

    private UUID insertRun() {
        UUID runId = UUID.randomUUID();
        template.update("INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, "
                + "status, created_at, version) VALUES (?, 'RFETM', '2025-2026', '{\"scopes\":[]}'::jsonb, "
                + "'MANUAL', 'ana', 'QUEUED', now(), 0)", runId);
        template.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status) "
                + "VALUES (?, ?, 0, 'season', 'Full season', '{\"scopes\":[]}'::jsonb, 'PENDING')",
                UUID.randomUUID(), runId);
        return runId;
    }

    private UUID unitOf(UUID runId) {
        return template.queryForObject("SELECT id FROM pipeline.pipeline_unit WHERE run_id = ?", UUID.class, runId);
    }

    private void insertArtifact(UUID runId, String key, String purgedAtExpression) {
        template.update("INSERT INTO pipeline.run_artifact (id, run_id, unit_id, kind, storage_key, sha256, "
                + "size_bytes, created_at, purged_at) VALUES (?, ?, ?, 'ZIP', ?, ?, 1, now(), " + purgedAtExpression
                + ")", UUID.randomUUID(), runId, unitOf(runId), key, SHA);
    }

    private void insertStep(UUID runId, String kind, int attempt, String reused) {
        template.update("INSERT INTO pipeline.pipeline_step (id, run_id, unit_id, kind, attempt, status, "
                + "started_at, import_job_reused) VALUES (?, ?, ?, ?, ?, 'RUNNING', now(), " + reused + ")",
                UUID.randomUUID(), runId, unitOf(runId), kind, attempt);
    }

    @Test
    void flywayAppliedV9() {
        assertThat(template.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("9");
    }

    @Test
    void purgedAtIsNullForNewRowsAndMustNotPrecedeCreation() {
        UUID runId = insertRun();

        insertArtifact(runId, "a.zip", "NULL");
        insertArtifact(runId, "b.zip", "now() + interval '1 minute'");

        assertThat(template.queryForObject(
                "SELECT count(*) FROM pipeline.run_artifact WHERE purged_at IS NULL", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> insertArtifact(runId, "c.zip", "now() - interval '1 day'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void severalRowsMayShareAStorageKeyAcrossRuns() {
        insertArtifact(insertRun(), "shared.zip", "NULL");
        template.update("UPDATE pipeline.pipeline_run SET status = 'FAILED', finished_at = now(), started_at = now()");

        insertArtifact(insertRun(), "shared.zip", "NULL");

        assertThat(template.queryForObject(
                "SELECT count(*) FROM pipeline.run_artifact WHERE storage_key = 'shared.zip'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void importJobReusedIsOnlyAllowedOnImportStepsAndOldRowsReadNull() {
        UUID runId = insertRun();

        insertStep(runId, "IMPORT", 1, "true");
        insertStep(runId, "IMPORT", 2, "NULL");
        insertStep(runId, "INGEST", 1, "NULL");

        assertThatThrownBy(() -> insertStep(runId, "INGEST", 2, "false"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStep(runId, "FETCH_PACKAGE", 1, "true"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(template.queryForObject("SELECT import_job_reused FROM pipeline.pipeline_step WHERE kind = 'INGEST'",
                Boolean.class)).isNull();
    }
}
