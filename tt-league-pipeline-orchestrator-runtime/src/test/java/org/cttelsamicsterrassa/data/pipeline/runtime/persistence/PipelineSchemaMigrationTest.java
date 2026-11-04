package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class PipelineSchemaMigrationTest extends AbstractPersistenceTest {

    @Autowired
    JdbcTemplate template;

    @Test
    void flywayAppliedV1InSchemaPipeline() {
        List<String> versions = template.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);
        assertThat(versions).contains("1");
    }

    @Test
    void tablesAndActiveIndexExistInPipelineSchemaOnly() {
        List<String> tables = template.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'pipeline'", String.class);
        assertThat(tables).contains("pipeline_run", "pipeline_step", "run_artifact", "import_report");

        List<String> indexes = template.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'pipeline' AND tablename = 'pipeline_run'",
                String.class);
        assertThat(indexes).contains("ux_pipeline_run_active_source");

        Integer inPublic = template.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' "
                        + "AND table_name IN ('pipeline_run', 'pipeline_step', 'run_artifact', 'import_report')",
                Integer.class);
        assertThat(inPublic).isZero();
    }

    @Test
    void databaseRejectsUnknownStatus() {
        assertThatThrownBy(() -> insertRun("RFETM", "2025-2026", "MANUAL", "BOGUS", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBadSeason() {
        assertThatThrownBy(() -> insertRun("RFETM", "25-26", "MANUAL", "QUEUED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRetryWithoutOriginalRun() {
        assertThatThrownBy(() -> insertRun("RFETM", "2025-2026", "RETRY", "FAILED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsBadSha256() {
        UUID runId = insertRun("RFETM", "2025-2026", "MANUAL", "NO_CHANGES", null);
        UUID unitId = UUID.randomUUID();
        template.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status, "
                + "started_at, finished_at) VALUES (?, ?, 0, 'season', 'Full season', '{\"scopes\":[]}'::jsonb, "
                + "'NO_CHANGES', now(), now())", unitId, runId);
        assertThatThrownBy(() -> template.update(
                "INSERT INTO pipeline.run_artifact (id, run_id, unit_id, kind, storage_key, sha256, size_bytes, "
                        + "created_at) VALUES (?, ?, ?, 'ZIP', 'k.zip', ?, 1, now())",
                UUID.randomUUID(), runId, unitId, "NOT-A-HASH".repeat(7).substring(0, 64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertRun(String source, String season, String trigger, String status, UUID retryOf) {
        UUID id = UUID.randomUUID();
        template.update(
                "INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, "
                        + "retry_of_run_id, status, created_at) VALUES (?, ?, ?, '{\"scopes\":[]}'::jsonb, ?, 'u', "
                        + "?, ?, now())",
                id, source, season, trigger, retryOf, status);
        return id;
    }
}
