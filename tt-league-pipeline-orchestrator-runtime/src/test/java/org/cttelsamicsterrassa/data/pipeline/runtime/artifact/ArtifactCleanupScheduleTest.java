package org.cttelsamicsterrassa.data.pipeline.runtime.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactRetentionRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.core.retention.ArtifactCleanup;
import org.cttelsamicsterrassa.data.pipeline.core.retention.RetentionPolicy;
import org.cttelsamicsterrassa.data.pipeline.core.retention.RetentionRule;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

class ArtifactCleanupScheduleTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final Instant NOW = Instant.parse("2026-10-10T08:00:00Z");

    private final FakeRunClock clock = new FakeRunClock(NOW);
    private final InMemoryRunArtifactRepository rows = new InMemoryRunArtifactRepository();
    private final InMemoryArtifactRetentionRepository retention = new InMemoryArtifactRetentionRepository(rows);
    private final InMemoryArtifactStore store = new InMemoryArtifactStore();
    private final RecordingLockProvider lockProvider = new RecordingLockProvider();
    private ArtifactCleanupSchedule schedule;

    @AfterEach
    void stop() {
        if (schedule != null) {
            schedule.stop();
        }
    }

    private static PipelineOrchestratorProperties.Retention settings(String cron, String zone) {
        Map<ArtifactKind, PipelineOrchestratorProperties.Retention.Rule> rules = new EnumMap<>(ArtifactKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            rules.put(kind, new PipelineOrchestratorProperties.Retention.Rule(Duration.ofDays(1), null));
        }
        return new PipelineOrchestratorProperties.Retention(cron, zone, rules);
    }

    private ArtifactCleanupSchedule schedule() {
        Map<ArtifactKind, RetentionRule> rules = new EnumMap<>(ArtifactKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            rules.put(kind, new RetentionRule.MaxAge(Duration.ofDays(1)));
        }
        ArtifactCleanup cleanup = new ArtifactCleanup(retention, rows, store, new RetentionPolicy(rules), clock);
        schedule = new ArtifactCleanupSchedule(settings("0 0 3 * * *", "Europe/Madrid"), cleanup,
                new DefaultLockingTaskExecutor(lockProvider));
        return schedule;
    }

    private void oldArtifact(String key) {
        UUID runId = UUID.randomUUID();
        var stored = store.store(key, new ByteArrayInputStream(new byte[] {1}));
        rows.add(new RunArtifact(UUID.randomUUID(), runId, ArtifactKind.ZIP, key, stored.sha256(), 1,
                NOW.minus(Duration.ofDays(5))));
        retention.run(runId, PipelineSource.RFETM, "2025-2026", false);
    }

    @Test
    void theTriggerFollowsTheConfiguredCronAndZone() {
        CronTrigger trigger = schedule().trigger();

        Instant next = trigger.nextExecution(new SimpleTriggerContext(java.time.Clock.fixed(NOW, MADRID)));

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 10, 11, 3, 0, 0, 0, MADRID).toInstant());
    }

    @Test
    void aTickPurgesUnderTheCleanupLock() {
        oldArtifact("a.zip");

        schedule().runTick();

        assertThat(lockProvider.requested).singleElement().satisfies(lock -> {
            assertThat(lock.getName()).isEqualTo("pipeline-artifact-cleanup");
            assertThat(lock.getLockAtMostFor()).isEqualTo(Duration.ofMinutes(30));
        });
        assertThat(lockProvider.unlocked.get()).isEqualTo(1);
        assertThat(store.exists("a.zip")).isFalse();
        assertThat(rows.all()).allMatch(RunArtifact::isPurged);
    }

    @Test
    void doesNothingWhenAnotherInstanceHoldsTheLock() {
        oldArtifact("a.zip");
        lockProvider.available = false;

        schedule().runTick();

        assertThat(store.exists("a.zip")).isTrue();
        assertThat(lockProvider.requested).hasSize(1);
    }

    @Test
    void aFailureIsLoggedAndTheNextTickTriesAgain() {
        oldArtifact("a.zip");
        InMemoryArtifactRetentionRepository failing = new InMemoryArtifactRetentionRepository(rows) {
            int calls;

            @Override
            public synchronized List<org.cttelsamicsterrassa.data.pipeline.core.retention.RetainedArtifact> findUnpurged() {
                if (calls++ == 0) {
                    throw new IllegalStateException("database down");
                }
                return retention.findUnpurged();
            }
        };
        Map<ArtifactKind, RetentionRule> rules = new EnumMap<>(ArtifactKind.class);
        for (ArtifactKind kind : ArtifactKind.values()) {
            rules.put(kind, new RetentionRule.MaxAge(Duration.ofDays(1)));
        }
        schedule = new ArtifactCleanupSchedule(settings("0 0 3 * * *", "Europe/Madrid"),
                new ArtifactCleanup(failing, rows, store, new RetentionPolicy(rules), clock),
                new DefaultLockingTaskExecutor(lockProvider));

        schedule.runTick();
        assertThat(store.exists("a.zip")).isTrue();
        schedule.runTick();

        assertThat(store.exists("a.zip")).isFalse();
    }

    @Test
    void startAndStopAreIdempotentAndNothingRunsAtStartup() {
        oldArtifact("a.zip");
        ArtifactCleanupSchedule started = schedule();

        started.start();
        started.start();
        assertThat(started.isRunning()).isTrue();
        started.stop();
        started.stop();

        assertThat(started.isRunning()).isFalse();
        assertThat(lockProvider.requested).isEmpty();
        assertThat(store.exists("a.zip")).isTrue();
    }

    private static final class RecordingLockProvider implements LockProvider {

        final List<LockConfiguration> requested = new ArrayList<>();
        final AtomicInteger unlocked = new AtomicInteger();
        boolean available = true;

        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
            requested.add(lockConfiguration);
            return available ? Optional.of(unlocked::incrementAndGet) : Optional.empty();
        }
    }
}
