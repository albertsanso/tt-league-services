package org.cttelsamicsterrassa.data.pipeline.runtime.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.cttelsamicsterrassa.data.pipeline.runtime.persistence.PipelinePersistenceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Two lock providers on one database stand for two orchestrator instances. */
@PipelinePersistenceTest
class SchedulerLockPersistenceTest {

    private static final String RFETM = ScheduledRunTrigger.LOCK_PREFIX + "RFETM";

    @Autowired
    DataSource dataSource;

    @Autowired
    JdbcTemplate jdbc;

    private LockProvider instanceA;
    private LockProvider instanceB;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM pipeline.shedlock");
        instanceA = new ScheduleConfiguration().schedulerLockProvider(dataSource);
        instanceB = new ScheduleConfiguration().schedulerLockProvider(dataSource);
    }

    private static LockConfiguration lock(String name, Duration atMost, Duration atLeast) {
        return new LockConfiguration(ClockProvider.now(), name, atMost, atLeast);
    }

    @Test
    void anotherInstanceCannotTakeAHeldLock() {
        Optional<SimpleLock> held = instanceA.lock(lock(RFETM, Duration.ofMinutes(10), Duration.ZERO));
        assertThat(held).isPresent();

        assertThat(instanceB.lock(lock(RFETM, Duration.ofMinutes(10), Duration.ZERO))).isEmpty();

        held.get().unlock();
        assertThat(instanceB.lock(lock(RFETM, Duration.ofMinutes(10), Duration.ZERO))).isPresent();
    }

    @Test
    void onlyOneInstanceFiresTheSameTick() {
        AtomicInteger fired = new AtomicInteger();
        LockConfiguration tick = lock(RFETM, Duration.ofMinutes(10), Duration.ofSeconds(30));

        new DefaultLockingTaskExecutor(instanceA).executeWithLock((Runnable) fired::incrementAndGet, tick);
        // instance B's clock is a little late: the tick already ran and released, but lock-at-least-for still holds
        new DefaultLockingTaskExecutor(instanceB).executeWithLock((Runnable) fired::incrementAndGet,
                lock(RFETM, Duration.ofMinutes(10), Duration.ofSeconds(30)));

        assertThat(fired.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.shedlock WHERE name = ?", Integer.class,
                RFETM)).isEqualTo(1);
    }

    @Test
    void theLockIsFreeAgainAfterLockAtLeastFor() {
        instanceA.lock(lock(RFETM, Duration.ofSeconds(5), Duration.ofSeconds(1))).orElseThrow().unlock();

        assertThat(instanceB.lock(lock(RFETM, Duration.ofSeconds(5), Duration.ofSeconds(1)))).isEmpty();
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200))
                .until(() -> instanceB.lock(lock(RFETM, Duration.ofSeconds(5), Duration.ZERO)).isPresent());
    }

    @Test
    void aLockOfADeadInstanceExpiresAfterLockAtMostFor() {
        // instance A takes the lock and never releases it
        assertThat(instanceA.lock(lock(RFETM, Duration.ofSeconds(1), Duration.ZERO))).isPresent();

        assertThat(instanceB.lock(lock(RFETM, Duration.ofSeconds(1), Duration.ZERO))).isEmpty();
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200))
                .until(() -> instanceB.lock(lock(RFETM, Duration.ofSeconds(1), Duration.ZERO)).isPresent());
    }

    @Test
    void sourcesHaveIndependentLocks() {
        assertThat(instanceA.lock(lock(RFETM, Duration.ofMinutes(10), Duration.ZERO))).isPresent();

        assertThat(instanceB.lock(lock(ScheduledRunTrigger.LOCK_PREFIX + "FCTT", Duration.ofMinutes(10),
                Duration.ZERO))).isPresent();
    }
}
