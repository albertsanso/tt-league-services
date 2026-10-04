package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
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
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.junit.jupiter.api.Test;

class ScheduledRunTickTest {

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryPendingTriggerRepository pending = new InMemoryPendingTriggerRepository();
    private final StubOpenMatchDayScopeResolver resolver = new StubOpenMatchDayScopeResolver();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final RecordingPendingTriggerEvents events = new RecordingPendingTriggerEvents();
    private final FakeRunClock clock = new FakeRunClock();
    private final RunLauncher launcher = new RunLauncher(runs, dispatcher, clock, new RecordingObserver());
    private final TriggerRun triggerRun = new TriggerRun(runs, pending, resolver, launcher, events, clock);
    private final ScheduledRunTick tick = new ScheduledRunTick(triggerRun, "2025-2026");

    private PipelineRun activeRun(PipelineSource source) {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "alice", null, clock.now()));
    }

    @Test
    void idleSourceGetsAScheduledFullSeasonRun() {
        Outcome outcome = tick.tick(PipelineSource.RFETM);

        assertThat(outcome).isInstanceOfSatisfying(Outcome.Created.class, created -> {
            PipelineRun run = created.run();
            assertThat(run.source()).isEqualTo(PipelineSource.RFETM);
            assertThat(run.season()).isEqualTo("2025-2026");
            assertThat(run.trigger()).isEqualTo(RunTrigger.SCHEDULED);
            assertThat(run.requestedBy()).isEqualTo(ScheduledRunTick.REQUESTED_BY);
            assertThat(run.scope().isFullSeason()).isTrue();
            assertThat(run.force()).isFalse();
            assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
            assertThat(dispatcher.dispatched).containsExactly(run.id());
        });
    }

    @Test
    void sourceWithAnActiveRunIsSkippedWithoutAPendingTrigger() {
        PipelineRun active = activeRun(PipelineSource.BCNESA);

        Outcome outcome = tick.tick(PipelineSource.BCNESA);

        assertThat(outcome).isInstanceOfSatisfying(Outcome.Rejected.class, rejected -> {
            assertThat(rejected.code()).isEqualTo(TriggerRun.ACTIVE_RUN);
            assertThat(rejected.activeRunId()).isEqualTo(active.id());
        });
        assertThat(runs.findActiveBySource(PipelineSource.BCNESA)).contains(active);
        assertThat(dispatcher.dispatched).isEmpty();
        assertThat(pending.findAll()).isEmpty();
        assertThat(events.events).isEmpty();
    }

    @Test
    void tickTouchesOnlyItsOwnSource() {
        tick.tick(PipelineSource.FCTT);

        assertThat(runs.findActiveBySource(PipelineSource.FCTT)).isPresent();
        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).isEmpty();
        assertThat(runs.findActiveBySource(PipelineSource.BCNESA)).isEmpty();
    }

    @Test
    void invalidSeasonFailsConstruction() {
        assertThatThrownBy(() -> new ScheduledRunTick(triggerRun, "2025-2027"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("season");
        assertThatThrownBy(() -> new ScheduledRunTick(triggerRun, null)).isInstanceOf(RuntimeException.class);
    }
}
