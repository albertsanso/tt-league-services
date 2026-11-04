package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/** The V3 migration: the ShedLock table {@code pipeline.shedlock}. */
class SchedulerLockMigrationTest extends AbstractPersistenceTest {

    @BeforeEach
    void clearLocks() {
        jdbc.update("DELETE FROM pipeline.shedlock");
    }

    @Test
    void flywayAppliedV3() {
        assertThat(jdbc.queryForList(
                "SELECT version FROM pipeline.flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class)).contains("3");
    }

    @Test
    void createsTheShedLockColumns() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'pipeline' AND table_name = 'shedlock' ORDER BY ordinal_position",
                String.class)).containsExactly("name", "lock_until", "locked_at", "locked_by");
    }

    @Test
    void theLockNameIsThePrimaryKey() {
        String insert = "INSERT INTO pipeline.shedlock (name, lock_until, locked_at, locked_by) "
                + "VALUES ('pipeline-schedule-RFETM', now(), now(), 'host-a')";
        jdbc.update(insert);

        assertThatThrownBy(() -> jdbc.update(insert)).isInstanceOf(DuplicateKeyException.class);
    }
}
