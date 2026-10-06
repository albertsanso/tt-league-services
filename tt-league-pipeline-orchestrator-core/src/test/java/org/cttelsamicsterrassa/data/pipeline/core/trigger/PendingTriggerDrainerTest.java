package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.StubOpenMatchDayScopeResolver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerEvents;
import org.junit.jupiter.api.Test;

class PendingTriggerDrainerTest {

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryPendingTriggerRepository pending = new InMemoryPendingTriggerRepository();
    private final FakeRunClock clock = new FakeRunClock();
    private final PendingTriggerDrainer drainer;

    PendingTriggerDrainerTest() {
        TriggerRun[] holder = new TriggerRun[1];
        RunLauncher launcher = new RunLauncher(runs, new RecordingDispatcher(), clock, RunObserver.none());
        holder[0] = new TriggerRun(runs, pending, new StubOpenMatchDayScopeResolver(), launcher,
                PendingTriggerEvents.none(), clock);
        drainer = new PendingTriggerDrainer(() -> holder[0]);
    }

    private PipelineRun activeRun() {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.SCHEDULED, "system:scheduler", null, clock.now()));
    }

    private void queuePending() {
        pending.add(new PendingTrigger(PipelineSource.RFETM, "2025-2026", ScopeType.FULL_SEASON, List.of(), false,
                "alice", clock.now()));
    }

    @Test
    void ignoresNonTerminalChanges() {
        PipelineRun run = activeRun();
        queuePending();

        drainer.runChanged(run);
        drainer.runChanged(runs.update(run.start(clock.now())));

        assertThat(pending.findAll()).hasSize(1);
    }

    @Test
    void launchesThePendingTriggerWhenTheRunEnds() {
        PipelineRun run = activeRun();
        queuePending();
        PipelineRun failed = runs.update(run.fail(new RunError("E", "boom"), clock.now()));

        drainer.runChanged(failed);

        assertThat(pending.findAll()).isEmpty();
        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).get()
                .satisfies(next -> assertThat(next.requestedBy()).isEqualTo("alice"));
    }
}
