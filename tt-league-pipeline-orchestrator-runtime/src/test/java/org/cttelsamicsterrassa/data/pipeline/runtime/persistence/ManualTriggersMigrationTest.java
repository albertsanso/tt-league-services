package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The V2 migration: {@code force}, the listing index and {@code pending_trigger}. */
class ManualTriggersMigrationTest extends AbstractPersistenceTest {

    @Autowired
    JdbcTemplate template;

    @Test
    void flywayAppliedV2() {
        assertThat(template.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("2");
    }

    @Test
    void forceDefaultsToFalseAndIsNotNull() {
        template.update("INSERT INTO pipeline.pipeline_run (id, source, season, scope, trigger, requested_by, "
                + "status, created_at) VALUES (gen_random_uuid(), 'RFETM', '2025-2026', '{\"scopes\":[]}', "
                + "'MANUAL', 'u', 'FAILED', now())");

        assertThat(template.queryForObject("SELECT force FROM pipeline.pipeline_run", Boolean.class)).isFalse();
        assertThat(template.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'pipeline' AND tablename = 'pipeline_run'",
                String.class)).contains("ix_pipeline_run_created");
    }

    private void insertPending(String source, String season, String scopeType) {
        template.update("INSERT INTO pipeline.pending_trigger (source, season, scope_type, filters, force, "
                + "requested_by, requested_at) VALUES (?, ?, ?, '{\"scopes\":[]}', false, 'u', now())",
                source, season, scopeType);
    }

    @Test
    void pendingTriggerEnforcesPrimaryKeyAndChecks() {
        insertPending("RFETM", "2025-2026", "FULL_SEASON");

        assertThatThrownBy(() -> insertPending("RFETM", "2025-2026", "FULL_SEASON"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPending("OTHER", "2025-2026", "FULL_SEASON"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPending("FCTT", "2025/2026", "FULL_SEASON"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPending("FCTT", "2025-2026", "EVERYTHING"))
                .isInstanceOf(DataIntegrityViolationException.class);
        List<String> sources = template.queryForList("SELECT source FROM pipeline.pending_trigger", String.class);
        assertThat(sources).containsExactly("RFETM");
    }
}
