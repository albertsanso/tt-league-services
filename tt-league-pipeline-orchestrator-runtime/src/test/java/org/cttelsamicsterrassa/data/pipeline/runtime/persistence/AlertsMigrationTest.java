package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** The V7 migration: {@code pipeline.alert}. */
class AlertsMigrationTest extends AbstractPersistenceTest {

    private UUID insert(String kind, String key, String source, boolean cleared) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO pipeline.alert (id, kind, condition_key, source, title, detail, raised_at, "
                + "cleared_at, version) VALUES (?, ?, ?, ?, 'title', 'detail', now(), "
                + "CASE WHEN ? THEN now() ELSE NULL END, 0)", id, kind, key, source, cleared);
        return id;
    }

    @Test
    void flywayAppliedV7() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("7");
    }

    @Test
    void createsTheAlertColumns() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'pipeline' AND table_name = 'alert' ORDER BY ordinal_position",
                String.class)).containsExactly("id", "kind", "condition_key", "source", "season", "title", "detail",
                "raised_at", "notified_at", "notify_attempts", "last_failure", "cleared_at", "version");
    }

    @Test
    void theChecksRejectUnknownKindsSourcesAndNegativeAttempts() {
        insert("MATCH_DAY_CLOSED", "a", "FCTT", false);
        insert("RUN_FAILURES", "FCTT", null, false);

        assertThatThrownBy(() -> insert("OTHER", "b", "FCTT", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("RUN_FAILURES", "c", "OTHER", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE pipeline.alert SET notify_attempts = -1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anActiveAlertIsUniquePerKindAndKeyButAClearedOneIsNot() {
        insert("RUN_FAILURES", "FCTT", "FCTT", false);

        assertThatThrownBy(() -> insert("RUN_FAILURES", "FCTT", "FCTT", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        insert("RUN_FAILURES", "RFETM", "RFETM", false);
        insert("NO_RECENT_SUCCESS", "FCTT", "FCTT", false);
        insert("RUN_FAILURES", "FCTT", "FCTT", true);
        insert("RUN_FAILURES", "FCTT", "FCTT", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.alert", Integer.class)).isEqualTo(5);
    }

    @Test
    void aSecondAlertCanBeRaisedAfterTheFirstWasCleared() {
        UUID first = insert("RUN_FAILURES", "FCTT", "FCTT", false);
        jdbc.update("UPDATE pipeline.alert SET cleared_at = now() WHERE id = ?", first);

        insert("RUN_FAILURES", "FCTT", "FCTT", false);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pipeline.alert WHERE cleared_at IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void thereAreNoForeignKeysToTheTrackerOrRunTables() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_schema = 'pipeline' AND table_name = 'alert' AND constraint_type = 'FOREIGN KEY'",
                Integer.class)).isZero();
    }
}
