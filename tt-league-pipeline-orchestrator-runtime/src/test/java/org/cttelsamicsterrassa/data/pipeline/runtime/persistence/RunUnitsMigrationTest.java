package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** The V10 migration: {@code pipeline_unit}, the new run statuses and trigger, unit ownership and the alert kind. */
class RunUnitsMigrationTest extends AbstractPersistenceTest {

    private static final String KEY = "0123456789abcdef".repeat(4);

    private UUID insertRun(String source, String trigger, String status, UUID retryOfRun, UUID retryOfUnit) {
        UUID id = UUID.randomUUID();
        boolean started = !status.equals("QUEUED");
        boolean finished = !status.equals("QUEUED") && !status.equals("RUNNING");
        boolean failed = status.equals("FAILED");
        jdbc.update("INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, "
                + "retry_of_run_id, retry_of_unit_id, status, created_at, started_at, finished_at, error_code, "
                + "error_message) VALUES (?, ?, '2025-2026', '{\"scopes\":[]}'::jsonb, ?, 'u', ?, ?, ?, ?, ?, ?, ?, ?)",
                id, source, trigger, retryOfRun, retryOfUnit, status, Timestamp.from(T0),
                started ? Timestamp.from(T0) : null, finished ? Timestamp.from(T0.plusSeconds(60)) : null,
                failed ? "E" : null, failed ? "boom" : null);
        return id;
    }

    private UUID run() {
        return insertRun("BCNESA", "MANUAL", "FAILED", null, null);
    }

    /** One unit insert with the given overrides on top of a valid PENDING unit. */
    private void insertUnit(UUID runId, int ordinal, String key, String status, String timings, String extra) {
        jdbc.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status"
                + (timings.isEmpty() ? "" : ", " + timings.split("\\|")[0])
                + (extra.isEmpty() ? "" : ", " + extra.split("\\|")[0]) + ") VALUES (?, ?, ?, ?, 'label', "
                + "'{\"scopes\":[]}'::jsonb, ?" + (timings.isEmpty() ? "" : ", " + timings.split("\\|")[1])
                + (extra.isEmpty() ? "" : ", " + extra.split("\\|")[1]) + ")",
                UUID.randomUUID(), runId, ordinal, key, status);
    }

    private static final String RUNNING_TIMINGS = "started_at|now()";
    private static final String FINISHED_TIMINGS = "started_at, finished_at|now(), now()";

    @Test
    void flywayAppliedV10AndTheNewTableExists() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("10");
        assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'pipeline'", String.class))
                .contains("pipeline_unit");
        assertThat(jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'pipeline' AND tablename = 'pipeline_unit'",
                String.class)).contains("ix_pipeline_unit_key_finished", "ix_pipeline_unit_finished",
                "uq_pipeline_unit_run_ordinal");
    }

    @Test
    void theActiveRunIndexCoversOnlyQueuedAndRunning() {
        String definition = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname = 'pipeline' "
                + "AND indexname = 'ux_pipeline_run_active_source'", String.class);

        assertThat(definition).contains("QUEUED").contains("RUNNING").doesNotContain("PACKED")
                .doesNotContain("IMPORTING").contains("UNIQUE");
    }

    @Test
    void runStatusesAreTheNewSetAndTheOldIntermediateOnesAreRejected() {
        insertRun("RFETM", "MANUAL", "QUEUED", null, null);
        insertRun("BCNESA", "MANUAL", "RUNNING", null, null);
        for (String legacy : List.of("RUNNING_INGEST", "PACKED", "IMPORTING", "BOGUS")) {
            assertThatThrownBy(() -> insertRun("FCTT", "MANUAL", legacy, null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertRun("RFETM", "MANUAL", "RUNNING", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aUnitRetryRunNeedsItsOriginalRunAndUnitAndNoOtherTriggerMayCarryThem() {
        UUID original = run();
        UUID unit = UUID.randomUUID();
        jdbc.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status, "
                + "started_at, finished_at, error_code, error_message) VALUES (?, ?, 0, 'season', 'Full season', "
                + "'{\"scopes\":[]}'::jsonb, 'FAILED', now(), now(), 'E', 'boom')", unit, original);

        insertRun("BCNESA", "UNIT_RETRY", "FAILED", original, unit);

        assertThatThrownBy(() -> insertRun("BCNESA", "UNIT_RETRY", "FAILED", original, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRun("BCNESA", "UNIT_RETRY", "FAILED", null, unit))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRun("BCNESA", "RETRY", "FAILED", original, unit))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRun("BCNESA", "MANUAL", "FAILED", null, unit))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertRun("BCNESA", "RETRY", "FAILED", original, null);
        assertThatThrownBy(() -> insertRun("BCNESA", "UNIT_RETRY", "FAILED", original, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aUnitHasAUniqueOrdinalPerRunAndARealKeyAndStatus() {
        UUID run = run();
        insertUnit(run, 0, "season", "PENDING", "", "");
        insertUnit(run, 1, KEY, "PENDING", "", "");
        insertUnit(run, 2, "legacy", "PENDING", "", "");

        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "PENDING", "", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 3, "not-a-key", "PENDING", "", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 3, KEY.toUpperCase(), "PENDING", "", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 3, KEY, "BOGUS", "", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, -1, KEY, "PENDING", "", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(UUID.randomUUID(), 0, KEY, "PENDING", "", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theUnitTimingRulesMirrorTheAggregate() {
        UUID run = run();
        // a pending or skipped unit never started; a running unit has a start and no finish
        insertUnit(run, 0, KEY, "RUNNING_INGEST", RUNNING_TIMINGS, "");
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "PENDING", RUNNING_TIMINGS, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "RUNNING_INGEST", "", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        // finished exactly for the terminal statuses
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "RUNNING_INGEST", FINISHED_TIMINGS, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "NO_CHANGES", RUNNING_TIMINGS, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        // finishing before starting
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "NO_CHANGES",
                "started_at, finished_at|now(), now() - interval '1 hour'", ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertUnit(run, 1, KEY, "NO_CHANGES", FINISHED_TIMINGS, "");
    }

    @Test
    void theUnitErrorAndImportJobRulesMirrorTheAggregate() {
        UUID run = run();
        String failed = "error_code, error_message|'E', 'boom'";
        // FAILED and SKIPPED carry an error, everything else none
        insertUnit(run, 0, KEY, "FAILED", FINISHED_TIMINGS, failed);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "FAILED", FINISHED_TIMINGS, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "NO_CHANGES", FINISHED_TIMINGS, failed))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "FAILED", FINISHED_TIMINGS, "error_code|'E'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status, "
                + "finished_at, error_code, error_message) VALUES (?, ?, 2, ?, 'l', '{\"scopes\":[]}'::jsonb, "
                + "'SKIPPED', now(), 'UNIT_SKIPPED', 'skipped')", UUID.randomUUID(), run, KEY);
        // an import job is required from IMPORTING on and forbidden before the import and for skipped units
        String job = "import_job_id|'" + UUID.randomUUID() + "'";
        assertThatThrownBy(() -> insertUnit(run, 3, KEY, "IMPORTING", RUNNING_TIMINGS, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertUnit(run, 3, KEY, "IMPORTING", RUNNING_TIMINGS, job);
        assertThatThrownBy(() -> insertUnit(run, 4, KEY, "SUCCEEDED", FINISHED_TIMINGS, ""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 4, KEY, "PACKED", RUNNING_TIMINGS, job))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 4, KEY, "NO_CHANGES", FINISHED_TIMINGS, job))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a failed unit may or may not have a job
        insertUnit(run, 4, KEY, "FAILED", FINISHED_TIMINGS,
                "error_code, error_message, import_job_id|'E', 'boom', '" + UUID.randomUUID() + "'");
    }

    @Test
    void progressIsConsistentAndOnlyAllowedWhileRunning() {
        UUID run = run();
        String progress = "progress_step, progress_stage, progress_items, progress_total, progress_current, "
                + "progress_updated_at|'INGEST', 'DOWNLOAD', 3, 10, 'league', now()";
        insertUnit(run, 0, KEY, "RUNNING_INGEST", RUNNING_TIMINGS, progress);
        // all five columns or none, the step is a known kind, counts are non-negative and items never exceed the total
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "RUNNING_INGEST", RUNNING_TIMINGS, "progress_step|'INGEST'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "RUNNING_INGEST", RUNNING_TIMINGS,
                "progress_stage|'DOWNLOAD'")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "RUNNING_INGEST", RUNNING_TIMINGS,
                "progress_step, progress_items, progress_updated_at|'BOGUS', 1, now()"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "RUNNING_INGEST", RUNNING_TIMINGS,
                "progress_step, progress_items, progress_updated_at|'INGEST', -1, now()"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 1, KEY, "RUNNING_INGEST", RUNNING_TIMINGS,
                "progress_step, progress_items, progress_total, progress_updated_at|'INGEST', 5, 4, now()"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // an unknown total is allowed, and so are the other running statuses
        insertUnit(run, 1, KEY, "RUNNING_INGEST", RUNNING_TIMINGS,
                "progress_step, progress_items, progress_updated_at|'FETCH_PACKAGE', 0, now()");
        // pending and terminal units carry none
        assertThatThrownBy(() -> insertUnit(run, 2, KEY, "PENDING", "", progress))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUnit(run, 2, KEY, "NO_CHANGES", FINISHED_TIMINGS, progress))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stepsAreNumberedPerUnitAndKindAndNeedAUnit() {
        UUID run = run();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        for (UUID unit : List.of(first, second)) {
            jdbc.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status) "
                    + "VALUES (?, ?, ?, ?, 'l', '{\"scopes\":[]}'::jsonb, 'PENDING')", unit, run,
                    unit.equals(first) ? 0 : 1, KEY);
        }

        insertStep(run, first, "INGEST", 1);
        insertStep(run, second, "INGEST", 1);

        assertThatThrownBy(() -> insertStep(run, first, "INGEST", 1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertStep(run, UUID.randomUUID(), "INGEST", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertStep(UUID runId, UUID unitId, String kind, int attempt) {
        jdbc.update("INSERT INTO pipeline.pipeline_step (id, run_id, unit_id, kind, attempt, status, started_at) "
                + "VALUES (?, ?, ?, ?, ?, 'RUNNING', now())", UUID.randomUUID(), runId, unitId, kind, attempt);
    }

    @Test
    void anImportReportIsKeyedByUnitSoARunMayHaveSeveral() {
        UUID run = run();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status) "
                + "VALUES (?, ?, 0, ?, 'l', '{\"scopes\":[]}'::jsonb, 'PENDING'), "
                + "(?, ?, 1, ?, 'l', '{\"scopes\":[]}'::jsonb, 'PENDING')", first, run, KEY, second, run, KEY);

        insertReport(run, first);
        insertReport(run, second);

        assertThatThrownBy(() -> insertReport(run, first)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.import_report WHERE run_id = ?",
                Integer.class, run)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'pipeline' "
                + "AND tablename = 'import_report'", String.class)).contains("import_report_pkey", "ix_import_report_run");
    }

    private void insertReport(UUID runId, UUID unitId) {
        jdbc.update("INSERT INTO pipeline.import_report (run_id, unit_id, import_job_id, import_status, "
                + "files_seen, items_persisted, skipped, processor_failures, scheduled_created, upgraded_to_played, "
                + "rescheduled, partial_actas, invalid_actas, unresolved_pending_fixtures, issues, raw_report, "
                + "received_at) VALUES (?, ?, ?, 'SUCCEEDED', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, '[]'::jsonb, '{}'::jsonb, "
                + "now())", runId, unitId, UUID.randomUUID());
    }

    @Test
    void artifactsBelongToAUnitAndSeveralUnitsMayHoldTheirOwn() {
        UUID run = run();
        UUID unit = UUID.randomUUID();
        jdbc.update("INSERT INTO pipeline.pipeline_unit (id, run_id, ordinal, unit_key, label, scope, status) "
                + "VALUES (?, ?, 0, ?, 'l', '{\"scopes\":[]}'::jsonb, 'PENDING')", unit, run, KEY);
        String insert = "INSERT INTO pipeline.run_artifact (id, run_id, unit_id, kind, storage_key, sha256, "
                + "size_bytes, created_at) VALUES (?, ?, ?, 'ZIP', ?, ?, 1, now())";

        jdbc.update(insert, UUID.randomUUID(), run, unit, "a.zip", KEY);

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), run, UUID.randomUUID(), "b.zip", KEY))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = 'pipeline' "
                + "AND tablename = 'run_artifact'", String.class)).contains("ix_run_artifact_unit");
    }

    @Test
    void theUnitFailuresAlertKindIsAcceptedAndUnknownKindsStillAreNot() {
        String insert = "INSERT INTO pipeline.alert (id, kind, condition_key, title, detail, raised_at, version) "
                + "VALUES (?, ?, ?, 't', 'd', now(), 0)";

        jdbc.update(insert, UUID.randomUUID(), "UNIT_FAILURES", "BCNESA:" + KEY);
        jdbc.update(insert, UUID.randomUUID(), "RUN_FAILURES", "BCNESA");

        assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), "BOGUS", "x"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
