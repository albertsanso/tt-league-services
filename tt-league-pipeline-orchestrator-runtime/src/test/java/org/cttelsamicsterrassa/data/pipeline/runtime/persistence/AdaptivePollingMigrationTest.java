package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The V5 migration: {@code poll_schedule} and {@code poll_policy}. */
class AdaptivePollingMigrationTest extends AbstractPersistenceTest {

    @Autowired
    JdbcTemplate template;

    private UUID insertGroup(String source, String season, String scopeKey, String level, Long interval,
            String stoppedAt, String stopReason) {
        UUID id = UUID.randomUUID();
        template.update("INSERT INTO pipeline.poll_schedule (id, source, season, scope_key, kind, filter, "
                + "policy_level, interval_seconds, stopped_at, stop_reason, version, created_at, updated_at) VALUES "
                + "(?, ?, ?, ?, 'GROUP', '{\"scopes\":[{\"category\":\"c\"}]}'::jsonb, ?, ?, "
                + "CASE WHEN ?::text IS NULL THEN NULL ELSE now() END, ?, 0, now(), now())",
                id, source, season, scopeKey, level, interval, stoppedAt, stopReason);
        return id;
    }

    private UUID insertFull(String source, String season, String scopeKey, String level, boolean withFilter) {
        UUID id = UUID.randomUUID();
        template.update("INSERT INTO pipeline.poll_schedule (id, source, season, scope_key, kind, filter, "
                + "policy_level, interval_seconds, version, created_at, updated_at) VALUES (?, ?, ?, ?, "
                + "'FULL_REFRESH', CASE WHEN ? THEN '{\"scopes\":[{}]}'::jsonb ELSE NULL END, ?, 604800, 0, now(), "
                + "now())", id, source, season, scopeKey, withFilter, level);
        return id;
    }

    @Test
    void flywayAppliedV5() {
        assertThat(template.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("5");
    }

    @Test
    void theUnitIsUniquePerSourceSeasonAndScopeKey() {
        insertGroup("FCTT", "2026-2027", "abc", "OPEN", 86400L, null, null);

        assertThatThrownBy(() -> insertGroup("FCTT", "2026-2027", "abc", "OPEN", 86400L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertGroup("RFETM", "2026-2027", "abc", "OPEN", 86400L, null, null);
        insertGroup("FCTT", "2025-2026", "abc", "OPEN", 86400L, null, null);
        insertGroup("FCTT", "2026-2027", "def", "OPEN", 86400L, null, null);
        insertFull("FCTT", "2026-2027", "FULL_REFRESH", "FULL_REFRESH", false);
        assertThat(template.queryForObject("SELECT count(*) FROM pipeline.poll_schedule", Integer.class))
                .isEqualTo(5);
    }

    @Test
    void checksRejectInvalidValues() {
        assertThatThrownBy(() -> insertGroup("OTHER", "2026-2027", "a", "OPEN", 1L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGroup("FCTT", "2026/2027", "a", "OPEN", 1L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGroup("FCTT", "2026-2027", "a", "FAST", 1L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGroup("FCTT", "2026-2027", "a", "OPEN", 0L, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // a non-stopped unit needs an interval
        assertThatThrownBy(() -> insertGroup("FCTT", "2026-2027", "a", "OPEN", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void stoppedStateAndLevelMustAgree() {
        insertGroup("FCTT", "2026-2027", "stopped", "STOPPED", null, "now", "OVERDUE_LIMIT");

        assertThatThrownBy(() -> insertGroup("FCTT", "2026-2027", "a", "STOPPED", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGroup("FCTT", "2026-2027", "b", "OPEN", 1L, "now", "OVERDUE_LIMIT"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertGroup("FCTT", "2026-2027", "c", "STOPPED", null, "now", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theFullRefreshUnitHasNoFilterAndItsOwnKeyAndLevel() {
        insertFull("FCTT", "2026-2027", "FULL_REFRESH", "FULL_REFRESH", false);

        assertThatThrownBy(() -> insertFull("RFETM", "2026-2027", "FULL_REFRESH", "FULL_REFRESH", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFull("RFETM", "2026-2027", "other", "FULL_REFRESH", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertFull("RFETM", "2026-2027", "FULL_REFRESH", "OPEN", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aDeletedRunClearsThePendingRunReference() {
        UUID runId = UUID.randomUUID();
        template.update("INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, "
                + "status, created_at, version) VALUES (?, 'FCTT', '2026-2027', '{\"scopes\":[]}'::jsonb, "
                + "'SCHEDULED', 'system:polling', 'QUEUED', now(), 0)", runId);
        UUID id = insertGroup("FCTT", "2026-2027", "abc", "OPEN", 86400L, null, null);
        template.update("UPDATE pipeline.poll_schedule SET pending_run_id = ? WHERE id = ?", runId, id);
        assertThatThrownBy(() -> template.update("UPDATE pipeline.poll_schedule SET pending_run_id = ? WHERE id = ?",
                UUID.randomUUID(), id)).isInstanceOf(DataIntegrityViolationException.class);

        template.update("DELETE FROM pipeline.pipeline_run WHERE id = ?", runId);

        assertThat(template.queryForObject("SELECT pending_run_id FROM pipeline.poll_schedule WHERE id = ?",
                UUID.class, id)).isNull();
    }

    @Test
    void policyIsOnePerSourceAndNeedsAKnownSource() {
        template.update("INSERT INTO pipeline.poll_policy (source, settings, version, updated_by, updated_at) "
                + "VALUES ('FCTT', '{}'::jsonb, 1, 'ana', now())");

        assertThatThrownBy(() -> template.update("INSERT INTO pipeline.poll_policy (source, settings, version, "
                + "updated_by, updated_at) VALUES ('FCTT', '{}'::jsonb, 1, 'ana', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> template.update("INSERT INTO pipeline.poll_policy (source, settings, version, "
                + "updated_by, updated_at) VALUES ('OTHER', '{}'::jsonb, 1, 'ana', now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
