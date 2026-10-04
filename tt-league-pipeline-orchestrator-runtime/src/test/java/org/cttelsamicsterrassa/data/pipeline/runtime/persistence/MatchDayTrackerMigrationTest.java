package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** The V4 migration: {@code match_day}, {@code match_tracking} and {@code match_day_event}. */
class MatchDayTrackerMigrationTest extends AbstractPersistenceTest {

    @Autowired
    JdbcTemplate template;

    private UUID insertDay(String source, String season, String competition, Integer group, String phase, int round) {
        UUID id = UUID.randomUUID();
        template.update("INSERT INTO pipeline.match_day (id, source, season, competition, group_number, phase, round, "
                + "grace_days, state, created_at, last_recomputed_at) VALUES (?, ?, ?, ?, ?, ?, ?, 2, 'UPCOMING', "
                + "now(), now())", id, source, season, competition, group, phase, round);
        return id;
    }

    private UUID insertDay(String state, String closeReason, String closedBy, boolean opened) {
        UUID id = UUID.randomUUID();
        template.update("INSERT INTO pipeline.match_day (id, source, season, competition, round, grace_days, state, "
                + "close_reason, closed_at, closed_by, opened_at, created_at, last_recomputed_at) VALUES (?, 'FCTT', "
                + "'2026-2027', 'TERCERA', 1, 2, ?, ?, CASE WHEN ?::text IS NULL THEN NULL ELSE now() END, ?, "
                + "CASE WHEN ? THEN now() ELSE NULL END, now(), now())",
                id, state, closeReason, closedBy, closedBy, opened);
        return id;
    }

    private void insertMatch(UUID matchId, UUID dayId, String status, boolean reportedAt, String ignoredBy,
            boolean ignoredAt) {
        template.update("INSERT INTO pipeline.match_tracking (match_id, match_day_id, status, first_seen_at, "
                + "status_changed_at, last_seen_at, reported_at, ignored_at, ignored_by) VALUES (?, ?, ?, now(), "
                + "now(), now(), CASE WHEN ? THEN now() ELSE NULL END, CASE WHEN ? THEN now() ELSE NULL END, ?)",
                matchId, dayId, status, reportedAt, ignoredAt, ignoredBy);
    }

    private void insertEvent(UUID dayId, UUID matchId, String kind, String note) {
        template.update("INSERT INTO pipeline.match_day_event (id, match_day_id, match_id, kind, actor, occurred_at, "
                + "note) VALUES (?, ?, ?, ?, 'ana', now(), ?)", UUID.randomUUID(), dayId, matchId, kind, note);
    }

    @Test
    void flywayAppliedV4() {
        assertThat(template.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("4");
    }

    @Test
    void theJornadaKeyIsUniqueEvenWithoutGroupAndPhase() {
        insertDay("FCTT", "2026-2027", "TERCERA", null, null, 1);

        assertThatThrownBy(() -> insertDay("FCTT", "2026-2027", "TERCERA", null, null, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertDay("FCTT", "2026-2027", "TERCERA", null, "1a Fase", 1);
        insertDay("FCTT", "2026-2027", "TERCERA", 1, null, 1);
        insertDay("FCTT", "2026-2027", "TERCERA", null, null, 2);
        insertDay("RFETM", "2026-2027", "TERCERA", null, null, 1);
        insertDay("FCTT", "2025-2026", "TERCERA", null, null, 1);
        insertDay("FCTT", "2026-2027", "SEGONA", null, null, 1);
        assertThatThrownBy(() -> insertDay("FCTT", "2026-2027", "TERCERA", null, "1a Fase", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(template.queryForObject("SELECT count(*) FROM pipeline.match_day", Integer.class)).isEqualTo(7);
    }

    @Test
    void matchDayChecksRejectInvalidValues() {
        assertThatThrownBy(() -> insertDay("OTHER", "2026-2027", "T", null, null, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay("FCTT", "2026/2027", "T", null, null, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay("FCTT", "2026-2027", "T", null, null, 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay("FCTT", "2026-2027", "T", 0, null, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> template.update("UPDATE pipeline.match_day SET grace_days = -1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void matchDayDatesComeInOrderedPairs() {
        UUID id = insertDay("FCTT", "2026-2027", "TERCERA", 1, null, 1);

        assertThatThrownBy(() -> template.update(
                "UPDATE pipeline.match_day SET first_date = '2026-10-04' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> template.update(
                "UPDATE pipeline.match_day SET first_date = '2026-10-05', last_date = '2026-10-04' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        template.update("UPDATE pipeline.match_day SET first_date = '2026-10-04', last_date = '2026-10-04' "
                + "WHERE id = ?", id);
    }

    @Test
    void closedStateIsConsistentWithItsCloseFields() {
        insertDay("UPCOMING", null, null, false);
        insertDay("OPEN", null, null, true);
        insertDay("CLOSED", "MANUAL", "ana", true);

        assertThatThrownBy(() -> insertDay("CLOSED", null, null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay("OPEN", "MANUAL", "ana", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay("OPEN", null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay("CLOSED", "WHATEVER", "ana", true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDay("DONE", null, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void matchTrackingEnforcesItsInvariantsAndReferences() {
        UUID day = insertDay("FCTT", "2026-2027", "TERCERA", 1, null, 1);
        insertMatch(UUID.randomUUID(), day, "SCHEDULED", false, null, false);
        insertMatch(UUID.randomUUID(), day, "REPORTED", true, null, false);
        insertMatch(UUID.randomUUID(), day, "OVERDUE", false, "ana", true);

        assertThatThrownBy(() -> insertMatch(UUID.randomUUID(), day, "REPORTED", false, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMatch(UUID.randomUUID(), day, "SCHEDULED", true, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMatch(UUID.randomUUID(), day, "OVERDUE", false, "ana", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMatch(UUID.randomUUID(), day, "OVERDUE", false, null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMatch(UUID.randomUUID(), day, "CANCELLED", false, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertMatch(UUID.randomUUID(), UUID.randomUUID(), "SCHEDULED", false, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reportedRunMustExistAndNeedsAReportedAt() {
        UUID day = insertDay("FCTT", "2026-2027", "TERCERA", 1, null, 1);

        assertThatThrownBy(() -> template.update("INSERT INTO pipeline.match_tracking (match_id, match_day_id, "
                + "status, first_seen_at, status_changed_at, last_seen_at, reported_at, reported_run_id) VALUES "
                + "(?, ?, 'REPORTED', now(), now(), now(), now(), ?)", UUID.randomUUID(), day, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> template.update("INSERT INTO pipeline.match_tracking (match_id, match_day_id, "
                + "status, first_seen_at, status_changed_at, last_seen_at, reported_run_id) VALUES "
                + "(?, ?, 'SCHEDULED', now(), now(), now(), ?)", UUID.randomUUID(), day, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventsKeepTheirMatchIdWithoutAForeignKeyAndNotesNeedText() {
        UUID day = insertDay("FCTT", "2026-2027", "TERCERA", 1, null, 1);
        UUID removedMatch = UUID.randomUUID();

        insertEvent(day, removedMatch, "MATCH_REMOVED", null);
        insertEvent(day, null, "NOTE", "called the club");

        assertThatThrownBy(() -> insertEvent(day, null, "NOTE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEvent(day, null, "SHOUTED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEvent(UUID.randomUUID(), null, "OPENED", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(template.queryForObject("SELECT count(*) FROM pipeline.match_day_event WHERE match_id = ?",
                Integer.class, removedMatch)).isEqualTo(1);
    }
}
