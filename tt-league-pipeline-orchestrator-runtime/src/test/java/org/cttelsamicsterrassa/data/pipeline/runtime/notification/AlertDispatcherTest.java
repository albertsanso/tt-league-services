package org.cttelsamicsterrassa.data.pipeline.runtime.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertEvaluator;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertSettings;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notification;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.Notifier;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryAlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryMatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingNotifier;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDay;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AlertDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final InMemoryAlertRepository alerts = new InMemoryAlertRepository();
    private final InMemoryMatchDayRepository matchDays = new InMemoryMatchDayRepository();
    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final FakeRunClock clock = new FakeRunClock(NOW);
    private final AlertSettings settings =
            new AlertSettings(Duration.ofHours(48), Duration.ofHours(24), Duration.ofDays(1));
    private AlertDispatcher dispatcher;

    @AfterEach
    void stop() {
        if (dispatcher != null) {
            dispatcher.stop();
        }
    }

    private AlertDispatcher started(AlertEvaluator evaluator, Notifier sender) {
        dispatcher = new AlertDispatcher(evaluator, sender);
        dispatcher.start();
        return dispatcher;
    }

    private AlertEvaluator evaluator() {
        return new AlertEvaluator(alerts, matchDays, runs, notifier, clock, settings);
    }

    private void failTwice() {
        for (int i = 2; i >= 1; i--) {
            Instant finished = NOW.minusSeconds(i * 600L);
            PipelineRun run = PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027",
                    RunScope.fullSeason(), false, RunTrigger.SCHEDULED, "system", null, finished.minusSeconds(60));
            runs.create(run.fail(new RunError("IMPORT_FAILED", "boom"), finished));
        }
    }

    @Test
    void anEvaluationRequestRunsOnTheWorkerAndSendsTheAlert() throws Exception {
        failTwice();
        started(evaluator(), notifier).request();
        dispatcher.awaitIdle();

        assertThat(notifier.sent()).hasSize(1);
    }

    @Test
    void requestsWhileAPassIsQueuedCoalesceIntoOne() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicInteger passes = new AtomicInteger();
        InMemoryMatchDayRepository blocking = new InMemoryMatchDayRepository() {
            @Override
            public List<MatchDay> findByState(MatchDayState state) {
                if (passes.incrementAndGet() == 1) {
                    blocked.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return super.findByState(state);
            }
        };
        AlertEvaluator evaluator = new AlertEvaluator(alerts, blocking, runs, notifier, clock, settings);
        started(evaluator, notifier).request();
        assertThat(blocked.await(5, TimeUnit.SECONDS)).isTrue();

        for (int i = 0; i < 20; i++) {
            dispatcher.request();
        }
        release.countDown();
        dispatcher.awaitIdle();

        // the running pass plus exactly one queued pass
        assertThat(passes.get()).isEqualTo(2);
    }

    @Test
    void aDisabledDispatcherDoesNothing() throws Exception {
        dispatcher = AlertDispatcher.disabled();
        dispatcher.start();

        dispatcher.request();
        dispatcher.send(new Notification("Subject", "Body"));
        dispatcher.awaitIdle();

        assertThat(dispatcher.enabled()).isFalse();
        assertThat(dispatcher.isRunning()).isTrue();
        assertThat(notifier.attempts()).isZero();
    }

    @Test
    void aFailingPassDoesNotStopTheNextOne() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        InMemoryPipelineRunRepository failingOnce = new InMemoryPipelineRunRepository() {
            @Override
            public synchronized org.cttelsamicsterrassa.data.pipeline.core.run.RunPage find(
                    org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery query) {
                if (calls.getAndIncrement() == 0) {
                    throw new IllegalStateException("database exploded");
                }
                return super.find(query);
            }
        };
        AlertEvaluator evaluator = new AlertEvaluator(alerts, matchDays, failingOnce, notifier, clock, settings);
        started(evaluator, notifier).request();
        dispatcher.awaitIdle();

        dispatcher.request();
        dispatcher.awaitIdle();

        assertThat(calls.get()).isGreaterThan(1);
        assertThat(dispatcher.isRunning()).isTrue();
    }

    @Test
    void aDirectSendGoesThroughTheNotifierOnTheWorker() throws Exception {
        started(evaluator(), notifier).send(new Notification("Polling stopped", "Body"));
        dispatcher.awaitIdle();

        assertThat(notifier.sent()).extracting(Notification::subject).containsExactly("Polling stopped");
    }

    @Test
    void aDirectSendFailureIsLoggedOnlyAndTheDispatcherKeepsWorking() throws Exception {
        notifier.failWith(true);
        started(evaluator(), notifier).send(new Notification("First", "Body"));
        dispatcher.awaitIdle();

        notifier.failWith(false);
        dispatcher.send(new Notification("Second", "Body"));
        dispatcher.awaitIdle();

        assertThat(notifier.attempts()).isEqualTo(2);
        assertThat(notifier.sent()).extracting(Notification::subject).containsExactly("Second");
    }

    @Test
    void aRuntimeFailureOfTheNotifierIsContainedToo() throws Exception {
        Notifier exploding = notification -> {
            throw new IllegalStateException("boom");
        };
        started(evaluator(), exploding).send(new Notification("First", "Body"));
        dispatcher.awaitIdle();

        dispatcher.send(new Notification("Second", "Body"));
        dispatcher.awaitIdle();

        assertThat(dispatcher.isRunning()).isTrue();
    }

    @Test
    void requestsAfterStopAreDroppedWithoutThrowing() {
        failTwice();
        started(evaluator(), notifier);
        dispatcher.stop();

        dispatcher.request();
        dispatcher.send(new Notification("Late", "Body"));

        assertThat(dispatcher.isRunning()).isFalse();
        assertThat(notifier.attempts()).isZero();
    }
}
