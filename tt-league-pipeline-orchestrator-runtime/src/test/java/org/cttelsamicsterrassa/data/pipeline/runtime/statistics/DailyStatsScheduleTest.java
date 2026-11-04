package org.cttelsamicsterrassa.data.pipeline.runtime.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryDailyStatsRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryStatisticsReadRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.CorrectionFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStatsAggregator;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.MatchFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.RunFacts;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsSettings;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StepFacts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class DailyStatsScheduleTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    // 2026-10-10 10:00 in Madrid
    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");

    private static final java.time.Clock CLOCK = java.time.Clock.fixed(NOW, MADRID);

    private final InMemoryDailyStatsRepository stored = new InMemoryDailyStatsRepository();
    private final RecordingLockProvider lockProvider = new RecordingLockProvider();
    private final FakeRunClock clock = new FakeRunClock(NOW);
    private DailyStatsSchedule schedule;

    @AfterEach
    void stop() {
        if (schedule != null) {
            schedule.stop();
        }
    }

    private DailyStatsSchedule schedule(StatisticsSettings settings, InMemoryStatisticsReadRepository reads) {
        schedule = new DailyStatsSchedule(settings, new DailyStatsAggregator(reads, stored, clock, settings),
                new DefaultLockingTaskExecutor(lockProvider), Duration.ofHours(1));
        return schedule;
    }

    private DailyStatsSchedule schedule(int backfillDays) {
        return schedule(new StatisticsSettings(MADRID, LocalTime.of(0, 30), backfillDays),
                new InMemoryStatisticsReadRepository());
    }

    @Test
    void theTriggerFiresDailyAtTheConfiguredTimeInTheConfiguredZone() {
        StatisticsSettings settings = new StatisticsSettings(MADRID, LocalTime.of(3, 15), 31);
        CronTrigger trigger = schedule(settings, new InMemoryStatisticsReadRepository()).trigger();

        Instant next = trigger.nextExecution(new SimpleTriggerContext(CLOCK));

        // after 10:00 on the 10th the next 03:15 Madrid time is on the 11th (UTC+2 in October)
        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 11, 3, 15, 0, 0, MADRID).toInstant());
        assertThat(trigger.getExpression()).isEqualTo("0 15 3 * * *");
    }

    @Test
    void theTriggerFollowsTheZoneAcrossAnotherOffset() {
        StatisticsSettings settings = new StatisticsSettings(ZoneId.of("UTC"), LocalTime.of(0, 30), 31);

        Instant next = schedule(settings, new InMemoryStatisticsReadRepository()).trigger()
                .nextExecution(new SimpleTriggerContext(CLOCK));

        assertThat(next).isEqualTo(Instant.parse("2026-10-11T00:30:00Z"));
    }

    @Test
    void aTickCatchesUpUnderTheStatisticsLock() {
        schedule(3).runTick();

        assertThat(lockProvider.requested).singleElement().satisfies(lock -> {
            assertThat(lock.getName()).isEqualTo("pipeline-daily-stats");
            assertThat(lock.getLockAtMostFor()).isEqualTo(Duration.ofMinutes(10));
        });
        assertThat(lockProvider.unlocked.get()).isEqualTo(1);
        assertThat(stored.latestDate()).contains(LocalDate.parse("2026-10-09"));
        assertThat(stored.upsertCalls()).isEqualTo(3);
    }

    @Test
    void doesNothingWhenAnotherInstanceHoldsTheLock() {
        lockProvider.available = false;

        schedule(3).runTick();

        assertThat(stored.upsertCalls()).isZero();
        assertThat(lockProvider.requested).hasSize(1);
    }

    @Test
    void aFailureIsLoggedAndDoesNotStopTheNextTick() {
        InMemoryStatisticsReadRepository failing = new InMemoryStatisticsReadRepository() {
            int calls;

            @Override
            public List<RunFacts> terminalRunsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources) {
                if (calls++ == 0) {
                    throw new IllegalStateException("database down");
                }
                return super.terminalRunsFinishedBetween(from, to, sources);
            }

            @Override
            public List<StepFacts> stepsFinishedBetween(Instant from, Instant to, Set<PipelineSource> sources) {
                return List.of();
            }

            @Override
            public List<MatchFacts> matchesBySeason(Set<PipelineSource> sources, String season) {
                return List.of();
            }

            @Override
            public List<CorrectionFacts> importReportsReceivedBetween(
                    Instant from, Instant to, Set<PipelineSource> sources) {
                return List.of();
            }
        };
        schedule(new StatisticsSettings(MADRID, LocalTime.of(0, 30), 2), failing);

        schedule.runTick();
        assertThat(stored.upsertCalls()).isZero();

        schedule.runTick();
        assertThat(stored.latestDate()).contains(LocalDate.parse("2026-10-09"));
        assertThat(lockProvider.unlocked.get()).isEqualTo(2);
    }

    @Test
    void startsAndStopsItsPrivateScheduler() {
        schedule(0);

        schedule.start();
        schedule.start();
        assertThat(schedule.isRunning()).isTrue();

        schedule.stop();
        schedule.stop();
        assertThat(schedule.isRunning()).isFalse();
    }

    private static final class RecordingLockProvider implements LockProvider {

        final List<LockConfiguration> requested = new ArrayList<>();
        final AtomicInteger unlocked = new AtomicInteger();
        boolean available = true;

        @Override
        public synchronized Optional<SimpleLock> lock(LockConfiguration configuration) {
            requested.add(configuration);
            if (!available) {
                return Optional.empty();
            }
            return Optional.of(unlocked::incrementAndGet);
        }
    }
}
