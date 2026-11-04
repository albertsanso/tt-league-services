package org.cttelsamicsterrassa.data.pipeline.runtime.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayChangeSet;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayKey;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayWindow;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.CloseReason;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.RunRef;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.SourceSeason;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Schedule;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Schedule.SourceSchedule;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Tracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TrackerRecomputeScheduleTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final Tracker SETTINGS =
            new Tracker(Duration.ofMinutes(30), Duration.ofMinutes(10), Duration.ofSeconds(30));

    private record Request(PipelineSource source, String season, RunRef run) {
    }

    private final InMemoryMatchDayRepository repository = new InMemoryMatchDayRepository();
    private final List<Request> requests = new ArrayList<>();
    private final RecordingLockProvider lockProvider = new RecordingLockProvider();
    private TrackerRecomputeSchedule schedule;

    @AfterEach
    void stop() {
        if (schedule != null) {
            schedule.stop();
        }
    }

    private TrackerRecomputeSchedule schedule(Schedule scheduleSettings) {
        schedule = new TrackerRecomputeSchedule(SETTINGS, scheduleSettings, repository,
                (source, season, run) -> requests.add(new Request(source, season, run)),
                new DefaultLockingTaskExecutor(lockProvider));
        return schedule;
    }

    private static Schedule none() {
        return new Schedule(null, null, null, null, Map.of());
    }

    private void track(PipelineSource source, String season, int round, boolean closed) {
        MatchDay day = MatchDay.create(UUID.randomUUID(),
                new MatchDayKey(source, season, "TERCERA", 1, null, round), MatchDayWindow.undated(2), T0);
        MatchDay stored = closed ? day.close(CloseReason.MANUAL, "ana", T0) : day;
        repository.apply(new MatchDayChangeSet(List.of(stored), List.of(), java.util.Set.of(), List.of()));
    }

    @Test
    void ticksUnderTheTrackerLockWithTheConfiguredBounds() {
        track(PipelineSource.FCTT, "2026-2027", 1, false);

        schedule(none()).runTick();

        assertThat(lockProvider.requested).singleElement().satisfies(lock -> {
            assertThat(lock.getName()).isEqualTo("pipeline-tracker-recompute");
            assertThat(lock.getLockAtMostFor()).isEqualTo(Duration.ofMinutes(10));
            assertThat(lock.getLockAtLeastFor()).isEqualTo(Duration.ofSeconds(30));
        });
        assertThat(lockProvider.unlocked.get()).isEqualTo(1);
    }

    @Test
    void requestsARunlessRecomputeForEverySourceAndSeasonWithUnclosedDays() {
        track(PipelineSource.FCTT, "2026-2027", 1, false);
        track(PipelineSource.FCTT, "2026-2027", 2, false);
        track(PipelineSource.RFETM, "2025-2026", 1, false);
        track(PipelineSource.BCNESA, "2026-2027", 1, true);

        schedule(none()).runTick();

        assertThat(requests).containsExactlyInAnyOrder(
                new Request(PipelineSource.FCTT, "2026-2027", null),
                new Request(PipelineSource.RFETM, "2025-2026", null));
    }

    @Test
    void addsTheFixedScheduleSeasonOfEachScheduledSource() {
        track(PipelineSource.RFETM, "2025-2026", 1, false);
        Schedule fixed = new Schedule("2026-2027", "Europe/Madrid", Duration.ofMinutes(10), Duration.ofSeconds(30),
                Map.of(PipelineSource.FCTT, new SourceSchedule("0 0 7 * * *"),
                        PipelineSource.RFETM, new SourceSchedule("0 0 8 * * *")));

        schedule(fixed).runTick();

        assertThat(requests).containsExactlyInAnyOrder(
                new Request(PipelineSource.RFETM, "2025-2026", null),
                new Request(PipelineSource.FCTT, "2026-2027", null),
                new Request(PipelineSource.RFETM, "2026-2027", null));
        assertThat(schedule.targets()).contains(new SourceSeason(PipelineSource.FCTT, "2026-2027"));
    }

    @Test
    void doesNothingWhenNothingIsTrackedAndNothingIsScheduled() {
        schedule(none()).runTick();

        assertThat(requests).isEmpty();
    }

    @Test
    void doesNothingWhenAnotherInstanceHoldsTheLock() {
        track(PipelineSource.FCTT, "2026-2027", 1, false);
        lockProvider.available = false;

        schedule(none()).runTick();

        assertThat(requests).isEmpty();
        assertThat(lockProvider.requested).hasSize(1);
    }

    @Test
    void startsAndStopsItsPrivateScheduler() {
        schedule(none());

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
