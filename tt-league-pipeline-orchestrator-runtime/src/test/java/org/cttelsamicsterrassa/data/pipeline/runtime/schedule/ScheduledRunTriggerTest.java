package org.cttelsamicsterrassa.data.pipeline.runtime.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingPendingTriggerEvents;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.StubOpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScheduledRunTick;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Schedule;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Schedule.SourceSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class ScheduledRunTriggerTest {

    private static final Duration AT_MOST = Duration.ofMinutes(10);
    private static final Duration AT_LEAST = Duration.ofSeconds(30);

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryPendingTriggerRepository pending = new InMemoryPendingTriggerRepository();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final FakeRunClock clock = new FakeRunClock();
    private final TriggerRun triggerRun = new TriggerRun(runs, pending, new StubOpenMatchDayScopeResolver(),
            new RunLauncher(runs, dispatcher, clock, new RecordingObserver()), new RecordingPendingTriggerEvents(),
            clock);
    private final RecordingLockProvider lockProvider = new RecordingLockProvider();
    private ScheduledRunTrigger trigger;

    @AfterEach
    void stop() {
        if (trigger != null) {
            trigger.stop();
        }
    }

    private static Schedule schedule(Map<PipelineSource, SourceSchedule> sources) {
        return new Schedule("2025-2026", "Europe/Madrid", AT_MOST, AT_LEAST, sources);
    }

    private ScheduledRunTrigger trigger(Schedule schedule, LockingTaskExecutor locks) {
        trigger = new ScheduledRunTrigger(schedule, triggerRun, locks);
        return trigger;
    }

    private ScheduledRunTrigger rfetmAndFcttAtSeven() {
        return trigger(schedule(Map.of(
                        PipelineSource.RFETM, new SourceSchedule("0 0 7 * * *"),
                        PipelineSource.FCTT, new SourceSchedule("0 30 21 * * MON-FRI"))),
                new DefaultLockingTaskExecutor(lockProvider));
    }

    @Test
    void registersOnlySourcesWithACronUsingTheirExpressionAndZone() {
        ScheduledRunTrigger trigger = rfetmAndFcttAtSeven();

        trigger.start();

        assertThat(trigger.isRunning()).isTrue();
        assertThat(trigger.registeredSources()).containsExactlyInAnyOrder(PipelineSource.RFETM, PipelineSource.FCTT);
        CronTrigger rfetm = trigger.triggers().get(PipelineSource.RFETM);
        assertThat(rfetm.getExpression()).isEqualTo("0 0 7 * * *");
        // 07:00 in Madrid (UTC+1 in January) is 06:00 UTC
        SimpleTriggerContext january = new SimpleTriggerContext(
                Clock.fixed(Instant.parse("2026-01-10T00:00:00Z"), ZoneOffset.UTC));
        assertThat(rfetm.nextExecution(january)).isEqualTo(Instant.parse("2026-01-10T06:00:00Z"));
        assertThat(trigger.triggers().get(PipelineSource.FCTT).getExpression()).isEqualTo("0 30 21 * * MON-FRI");
    }

    @Test
    void withoutSchedulesNothingIsRegisteredAndNoSchedulerIsCreated() {
        ScheduledRunTrigger trigger = trigger(new Schedule(null, null, null, null, Map.of()),
                new DefaultLockingTaskExecutor(lockProvider));

        trigger.start();

        assertThat(trigger.isRunning()).isTrue();
        assertThat(trigger.registeredSources()).isEmpty();
        assertThat(trigger.schedulerActive()).isFalse();
        assertThatThrownBy(() -> trigger.runTick(PipelineSource.RFETM)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aTickTakesTheSourceLockAndCreatesAScheduledRun() {
        ScheduledRunTrigger trigger = rfetmAndFcttAtSeven();

        trigger.runTick(PipelineSource.RFETM);

        assertThat(lockProvider.requested).singleElement().satisfies(lock -> {
            assertThat(lock.getName()).isEqualTo("pipeline-schedule-RFETM");
            assertThat(lock.getLockAtMostFor()).isEqualTo(AT_MOST);
            assertThat(lock.getLockAtLeastFor()).isEqualTo(AT_LEAST);
        });
        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).hasValueSatisfying(run -> {
            assertThat(run.trigger()).isEqualTo(RunTrigger.SCHEDULED);
            assertThat(run.requestedBy()).isEqualTo(ScheduledRunTick.REQUESTED_BY);
            assertThat(run.season()).isEqualTo("2025-2026");
            assertThat(run.scope().isFullSeason()).isTrue();
        });
        assertThat(lockProvider.unlocked.get()).isEqualTo(1);
    }

    @Test
    void aTickWhoseLockIsHeldElsewhereDoesNothing() {
        ScheduledRunTrigger trigger = rfetmAndFcttAtSeven();
        lockProvider.available = false;

        trigger.runTick(PipelineSource.RFETM);

        assertThat(lockProvider.requested).hasSize(1);
        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).isEmpty();
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void aTickForASourceWithAnActiveRunIsSkipped() {
        ScheduledRunTrigger trigger = rfetmAndFcttAtSeven();
        PipelineRun active = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "alice", null, clock.now()));

        trigger.runTick(PipelineSource.FCTT);

        assertThat(runs.findActiveBySource(PipelineSource.FCTT)).contains(active);
        assertThat(dispatcher.dispatched).isEmpty();
        assertThat(pending.findAll()).isEmpty();
        assertThat(lockProvider.unlocked.get()).isEqualTo(1);
    }

    @Test
    void aFailedTickDoesNotCancelTheNextOne() throws InterruptedException {
        CountDownLatch secondTick = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        LockingTaskExecutor failingOnce = new DefaultLockingTaskExecutor(configuration -> {
            secondTick.countDown();
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("database unavailable");
            }
            return Optional.empty();
        });
        ScheduledRunTrigger trigger = trigger(
                schedule(Map.of(PipelineSource.BCNESA, new SourceSchedule("* * * * * *"))), failingOnce);

        trigger.start();

        assertThat(secondTick.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(calls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void stopCancelsTheTicksAndShutsTheSchedulerDown() {
        ScheduledRunTrigger trigger = rfetmAndFcttAtSeven();
        trigger.start();

        trigger.stop();

        assertThat(trigger.isRunning()).isFalse();
        assertThat(trigger.registeredSources()).isEmpty();
        assertThat(trigger.schedulerActive()).isFalse();
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
