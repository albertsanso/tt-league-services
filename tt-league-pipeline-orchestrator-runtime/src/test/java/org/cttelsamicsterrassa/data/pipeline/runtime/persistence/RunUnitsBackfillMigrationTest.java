package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * V10 applied over a V9 database that already holds runs: every run becomes one unit (ordinal 0) and its steps, artifacts
 * and import report move to it. Plain Flyway against a scratch database of the shared test container (the Spring context
 * always migrates its own database to the latest version, and a container per test class would only add load).
 */
class RunUnitsBackfillMigrationTest extends AbstractPersistenceTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final String FULL_SEASON = "{\"scopes\":[]}";
    private static final String SCOPED = "{\"scopes\":[{\"competitionId\":\"c1\"}]}";
    private static final String SHA = "ab".repeat(32);

    private static Flyway flyway(String url, String username, String password, String target) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .schemas("pipeline")
                .defaultSchema("pipeline")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    private static UUID insertRun(JdbcTemplate jdbc, String source, String scope, String status, String trigger,
            UUID retryOf, String ingestRunId, UUID importJobId, boolean failed) {
        UUID id = UUID.randomUUID();
        boolean started = !status.equals("QUEUED");
        boolean finished = !List.of("QUEUED", "RUNNING_INGEST", "PACKED", "IMPORTING").contains(status);
        jdbc.update("INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, "
                + "retry_of_run_id, status, created_at, started_at, finished_at, ingest_run_id, import_job_id, "
                + "error_code, error_message) VALUES (?, ?, '2025-2026', ?::jsonb, ?, 'u', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, source, scope, trigger, retryOf, status, Timestamp.from(T0),
                started ? Timestamp.from(T0.plusSeconds(5)) : null,
                finished ? Timestamp.from(T0.plusSeconds(65)) : null, ingestRunId, importJobId,
                failed ? "E" : null, failed ? "boom" : null);
        return id;
    }

    @Test
    void everyExistingRunBecomesOneUnitAndItsChildrenFollow() throws Exception {
        // a scratch database: the same server as the Spring context, but untouched by its migrations
        HikariDataSource shared = this.jdbc.getDataSource().unwrap(HikariDataSource.class);
        String database = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        this.jdbc.execute("CREATE DATABASE " + database);
        String url = shared.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + database + "$1");
        flyway(url, shared.getUsername(), shared.getPassword(), "9").migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(url, shared.getUsername(), shared.getPassword()));

        UUID job = UUID.randomUUID();
        UUID finished = insertRun(jdbc, "RFETM", FULL_SEASON, "SUCCEEDED", "SCHEDULED", null, "ing-1", job, false);
        UUID failed = insertRun(jdbc, "BCNESA", SCOPED, "FAILED", "MANUAL", null, "ing-2", null, true);
        UUID noChanges = insertRun(jdbc, "FCTT", FULL_SEASON, "NO_CHANGES", "MANUAL", null, "ing-3", null, false);
        UUID retry = insertRun(jdbc, "FCTT", SCOPED, "FAILED", "RETRY", noChanges, null, null, true);
        UUID queued = insertRun(jdbc, "FCTT", FULL_SEASON, "QUEUED", "MANUAL", null, null, null, false);
        // the three intermediate statuses of V1 are all active; the one-active-run-per-source index allows one per source
        UUID ingesting = insertRun(jdbc, "RFETM", SCOPED, "RUNNING_INGEST", "MANUAL", null, "ing-4", null, false);
        UUID importing = insertRun(jdbc, "BCNESA", FULL_SEASON, "IMPORTING", "MANUAL", null, "ing-5", job, false);

        jdbc.update("INSERT INTO pipeline.pipeline_step (id, run_id, kind, attempt, status, started_at) "
                + "VALUES (?, ?, 'INGEST', 1, 'SUCCEEDED', ?), (?, ?, 'INGEST', 2, 'RUNNING', ?), "
                + "(?, ?, 'IMPORT', 1, 'RUNNING', ?)", UUID.randomUUID(), finished, Timestamp.from(T0),
                UUID.randomUUID(), failed, Timestamp.from(T0), UUID.randomUUID(), finished, Timestamp.from(T0));
        jdbc.update("INSERT INTO pipeline.run_artifact (id, run_id, kind, storage_key, sha256, size_bytes, created_at) "
                + "VALUES (?, ?, 'ZIP', 'a.zip', ?, 10, ?)", UUID.randomUUID(), finished, SHA, Timestamp.from(T0));
        jdbc.update("INSERT INTO pipeline.import_report (run_id, import_job_id, import_status, files_seen, "
                + "items_persisted, skipped, processor_failures, scheduled_created, upgraded_to_played, rescheduled, "
                + "partial_actas, invalid_actas, unresolved_pending_fixtures, issues, raw_report, received_at) "
                + "VALUES (?, ?, 'SUCCEEDED', 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, '[]'::jsonb, '{}'::jsonb, ?)", finished,
                job, Timestamp.from(T0));

        flyway(url, shared.getUsername(), shared.getPassword(), "10").migrate();

        // one unit per run, ordinal 0, the run scope, the run timings and error
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.pipeline_unit", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.pipeline_unit WHERE ordinal <> 0",
                Integer.class)).isZero();
        Map<String, Object> season = unit(jdbc, finished);
        assertThat(season.get("unit_key")).isEqualTo("season");
        assertThat(season.get("label")).isEqualTo("Full season");
        assertThat(season.get("status")).isEqualTo("SUCCEEDED");
        assertThat(season.get("ingest_run_id")).isEqualTo("ing-1");
        assertThat(season.get("import_job_id")).isEqualTo(job);
        assertThat(season.get("version")).isEqualTo(0L);
        assertThat(((Timestamp) season.get("finished_at")).toInstant()).isEqualTo(T0.plusSeconds(65));
        Map<String, Object> scoped = unit(jdbc, failed);
        assertThat(scoped.get("unit_key")).isEqualTo("legacy");
        assertThat(scoped.get("label")).isEqualTo("Legacy scope");
        assertThat(scoped.get("status")).isEqualTo("FAILED");
        assertThat(scoped.get("error_code")).isEqualTo("E");
        assertThat(scoped.get("error_message")).isEqualTo("boom");
        assertThat(unit(jdbc, noChanges).get("status")).isEqualTo("NO_CHANGES");
        assertThat(unit(jdbc, queued).get("status")).isEqualTo("PENDING");
        assertThat(unit(jdbc, queued).get("started_at")).isNull();
        assertThat(unit(jdbc, ingesting).get("status")).isEqualTo("RUNNING_INGEST");
        assertThat(unit(jdbc, importing).get("status")).isEqualTo("IMPORTING");

        // a unit's progress starts empty
        assertThat(unit(jdbc, ingesting).get("progress_step")).isNull();

        // the intermediate run statuses collapse to RUNNING, everything else is kept
        assertThat(status(jdbc, queued)).isEqualTo("QUEUED");
        assertThat(status(jdbc, ingesting)).isEqualTo("RUNNING");
        assertThat(status(jdbc, importing)).isEqualTo("RUNNING");
        assertThat(status(jdbc, finished)).isEqualTo("SUCCEEDED");
        assertThat(status(jdbc, failed)).isEqualTo("FAILED");
        assertThat(status(jdbc, noChanges)).isEqualTo("NO_CHANGES");
        assertThat(status(jdbc, retry)).isEqualTo("FAILED");

        // children follow the ordinal 0 unit of their run
        UUID unitOfFinished = (UUID) season.get("id");
        assertThat(jdbc.queryForList("SELECT unit_id FROM pipeline.pipeline_step WHERE run_id = ?", UUID.class,
                finished)).containsOnly(unitOfFinished).hasSize(2);
        assertThat(jdbc.queryForList("SELECT unit_id FROM pipeline.pipeline_step WHERE run_id = ?", UUID.class,
                failed)).containsExactly((UUID) scoped.get("id"));
        assertThat(jdbc.queryForList("SELECT unit_id FROM pipeline.run_artifact WHERE run_id = ?", UUID.class,
                finished)).containsExactly(unitOfFinished);
        assertThat(jdbc.queryForList("SELECT unit_id FROM pipeline.import_report WHERE run_id = ?", UUID.class,
                finished)).containsExactly(unitOfFinished);

        // the legacy columns stay readable, and no new run keeps the old statuses
        assertThat(jdbc.queryForObject("SELECT ingest_run_id FROM pipeline.pipeline_run WHERE id = ?", String.class,
                finished)).isEqualTo("ing-1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.pipeline_run WHERE status IN "
                + "('RUNNING_INGEST', 'PACKED', 'IMPORTING')", Integer.class)).isZero();
    }

    private static Map<String, Object> unit(JdbcTemplate jdbc, UUID runId) {
        return jdbc.queryForMap("SELECT * FROM pipeline.pipeline_unit WHERE run_id = ?", runId);
    }

    private static String status(JdbcTemplate jdbc, UUID runId) {
        return jdbc.queryForObject("SELECT status FROM pipeline.pipeline_run WHERE id = ?", String.class, runId);
    }
}
