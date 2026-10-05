package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The V8 migration: step health columns, {@code import_report.amended_played} and {@code daily_stats}. */
class StatisticsMigrationTest extends AbstractPersistenceTest {

    @Autowired
    JdbcTemplate template;

    private UUID insertRun() {
        UUID runId = UUID.randomUUID();
        template.update("INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, "
                + "status, created_at, version) VALUES (?, 'FCTT', '2026-2027', '{\"scopes\":[]}'::jsonb, "
                + "'MANUAL', 'ana', 'QUEUED', now(), 0)", runId);
        return runId;
    }

    private void insertStep(UUID runId, String kind, int attempt, Long http, Long timeouts, Long parse) {
        template.update("INSERT INTO pipeline.pipeline_step (id, run_id, kind, attempt, status, started_at, "
                + "finished_at, http_errors, timeouts, parse_errors) VALUES (?, ?, ?, ?, 'SUCCEEDED', now(), now(), "
                + "?, ?, ?)", UUID.randomUUID(), runId, kind, attempt, http, timeouts, parse);
    }

    private void insertDaily(String date, String source, int runs, int failures, Long avg) {
        template.update("INSERT INTO pipeline.daily_stats (stat_date, source, runs, failures, matches_reported, "
                + "avg_time_to_report_seconds, pending_end_of_day, zone, computed_at) VALUES (?::date, ?, ?, ?, 0, ?, "
                + "0, 'Europe/Madrid', now())", date, source, runs, failures, avg);
    }

    @Test
    void flywayAppliedV8() {
        assertThat(template.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("8");
    }

    @Test
    void anIngestStepStoresAllThreeHealthValuesOrNone() {
        UUID runId = insertRun();

        insertStep(runId, "INGEST", 1, 3L, 2L, 1L);
        insertStep(runId, "INGEST", 2, null, null, null);
        insertStep(runId, "INGEST", 3, 0L, 0L, 0L);

        assertThat(template.queryForObject("SELECT count(*) FROM pipeline.pipeline_step", Integer.class))
                .isEqualTo(3);
        assertThatThrownBy(() -> insertStep(runId, "INGEST", 4, 1L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStep(runId, "INGEST", 5, null, 1L, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void healthIsRejectedOnANonIngestStepAndWhenNegative() {
        UUID runId = insertRun();

        assertThatThrownBy(() -> insertStep(runId, "IMPORT", 1, 1L, 1L, 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStep(runId, "FETCH_PACKAGE", 1, 0L, 0L, 0L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStep(runId, "INGEST", 1, -1L, 0L, 0L))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertStep(runId, "IMPORT", 2, null, null, null);
    }

    @Test
    void amendedPlayedDefaultsToZeroAndMustNotBeNegative() {
        UUID runId = insertRun();
        template.update("INSERT INTO pipeline.import_report (run_id, import_job_id, import_status, files_seen, "
                + "items_persisted, skipped, processor_failures, scheduled_created, upgraded_to_played, rescheduled, "
                + "partial_actas, invalid_actas, unresolved_pending_fixtures, issues, raw_report, received_at) "
                + "VALUES (?, ?, 'SUCCEEDED', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '[]'::jsonb, '{}'::jsonb, now())",
                runId, UUID.randomUUID());

        assertThat(template.queryForObject("SELECT amended_played FROM pipeline.import_report WHERE run_id = ?",
                Long.class, runId)).isZero();
        assertThatThrownBy(() -> template.update("UPDATE pipeline.import_report SET amended_played = -1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dailyStatsIsKeyedByDateAndSource() {
        insertDaily("2026-10-03", "FCTT", 3, 1, 3600L);
        insertDaily("2026-10-03", "RFETM", 0, 0, null);
        insertDaily("2026-10-04", "FCTT", 0, 0, null);

        assertThatThrownBy(() -> insertDaily("2026-10-03", "FCTT", 1, 0, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(template.queryForObject("SELECT count(*) FROM pipeline.daily_stats", Integer.class)).isEqualTo(3);
    }

    @Test
    void dailyStatsChecksRejectInvalidRows() {
        assertThatThrownBy(() -> insertDaily("2026-10-03", "OTHER", 0, 0, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDaily("2026-10-03", "FCTT", 1, 2, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDaily("2026-10-03", "FCTT", -1, 0, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDaily("2026-10-03", "FCTT", 1, 0, -5L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dailyStatsIsASnapshotWithoutForeignKeys() {
        Integer foreignKeys = template.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints WHERE table_schema = 'pipeline' "
                        + "AND table_name = 'daily_stats' AND constraint_type = 'FOREIGN KEY'", Integer.class);
        assertThat(foreignKeys).isZero();
    }

    @Test
    void theRangeReadIndexesExist() {
        List<String> indexes = template.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'pipeline'", String.class);
        assertThat(indexes).contains("ix_pipeline_run_finished", "ix_pipeline_step_finished",
                "ix_match_tracking_reported", "ix_import_report_received");
    }
}
