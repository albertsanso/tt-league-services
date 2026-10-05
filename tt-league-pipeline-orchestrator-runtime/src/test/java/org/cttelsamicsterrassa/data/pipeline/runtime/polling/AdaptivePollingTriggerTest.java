package org.cttelsamicsterrassa.data.pipeline.runtime.polling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties.Polling;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AdaptivePollingTriggerTest {

    private static final Duration AT_MOST = Duration.ofMinutes(7);
    private static final Duration AT_LEAST = Duration.ofSeconds(20);

    private final RecordingLockProvider lockProvider = new RecordingLockProvider();
    private final LockingTaskExecutor locks = new DefaultLockingTaskExecutor(lockProvider);
    private final List<PipelineSource> ticked = new ArrayList<>();
    private AdaptivePollingTrigger trigger;

    @AfterEach
    void stop() {
        if (trigger != null) {
            trigger.stop();
        }
    }

    private static Polling polling(PipelineSource... sources) {
        return new Polling(sources.length == 0 ? null : Set.of(sources), Duration.ofMinutes(5), AT_MOST, AT_LEAST,
                null, null);
    }

    private AdaptivePollingTrigger trigger(Polling polling) {
        trigger = new AdaptivePollingTrigger(polling, ticked::add, locks);
        return trigger;
    }

    @Test
    void noPolledSourceMeansNoSchedulerAndNoTick() {
        AdaptivePollingTrigger t = trigger(polling());

        t.start();

        assertThat(t.isRunning()).isTrue();
        assertThat(t.schedulerActive()).isFalse();
        t.stop();
        assertThat(t.isRunning()).isFalse();
        assertThat(ticked).isEmpty();
    }

    @Test
    void startAndStopManageThePrivateScheduler() {
        AdaptivePollingTrigger t = trigger(polling(PipelineSource.FCTT));

        t.start();
        t.start();
        assertThat(t.schedulerActive()).isTrue();

        t.stop();
        t.stop();
        assertThat(t.schedulerActive()).isFalse();
        assertThat(t.isRunning()).isFalse();
    }

    @Test
    void eachSourceTicksUnderItsOwnLock() {
        AdaptivePollingTrigger t = trigger(polling(PipelineSource.FCTT, PipelineSource.RFETM));

        t.runTick();

        assertThat(ticked).containsExactly(PipelineSource.RFETM, PipelineSource.FCTT);
        assertThat(lockProvider.requested).extracting(LockConfiguration::getName)
                .containsExactly("pipeline-polling-RFETM", "pipeline-polling-FCTT");
        assertThat(lockProvider.requested).allSatisfy(lock -> {
            assertThat(lock.getLockAtMostFor()).isEqualTo(AT_MOST);
            assertThat(lock.getLockAtLeastFor()).isEqualTo(AT_LEAST);
        });
        assertThat(lockProvider.unlocked).isEqualTo(2);
    }

    @Test
    void aSourceWhoseLockIsHeldElsewhereIsSkipped() {
        lockProvider.heldBy("pipeline-polling-RFETM");
        AdaptivePollingTrigger t = trigger(polling(PipelineSource.FCTT, PipelineSource.RFETM));

        t.runTick();

        assertThat(ticked).containsExactly(PipelineSource.FCTT);
    }

    @Test
    void aFailingSourceDoesNotStopTheOthers() {
        List<PipelineSource> seen = new ArrayList<>();
        trigger = new AdaptivePollingTrigger(polling(PipelineSource.RFETM, PipelineSource.BCNESA, PipelineSource.FCTT),
                source -> {
                    seen.add(source);
                    if (source == PipelineSource.RFETM) {
                        throw new IllegalStateException("boom");
                    }
                }, locks);

        trigger.runTick();

        assertThat(seen).containsExactly(PipelineSource.RFETM, PipelineSource.BCNESA, PipelineSource.FCTT);
        assertThat(lockProvider.unlocked).isEqualTo(3);
    }

    @Test
    void aSourceThatIsNotPolledCannotBeTicked() {
        AdaptivePollingTrigger t = trigger(polling(PipelineSource.FCTT));

        assertThatThrownBy(() -> t.runTick(PipelineSource.RFETM)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RFETM");
        assertThat(ticked).isEmpty();
    }

    /** Hands out every lock unless the name was marked as held by another instance. */
    private static final class RecordingLockProvider implements LockProvider {

        final List<LockConfiguration> requested = new ArrayList<>();
        int unlocked;
        private final Set<String> held = new java.util.HashSet<>();

        void heldBy(String name) {
            held.add(name);
        }

        @Override
        public Optional<SimpleLock> lock(LockConfiguration configuration) {
            requested.add(configuration);
            if (held.contains(configuration.getName())) {
                return Optional.empty();
            }
            return Optional.of(() -> unlocked++);
        }
    }
}
