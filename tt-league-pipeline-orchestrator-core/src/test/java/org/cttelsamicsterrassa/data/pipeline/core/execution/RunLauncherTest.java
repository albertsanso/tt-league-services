package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher.LaunchRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ActiveRunConflictException;
import org.junit.jupiter.api.Test;

class RunLauncherTest {

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final RunLauncher launcher = new RunLauncher(runs, dispatcher, new FakeRunClock());

    private static LaunchRequest request(PipelineSource source) {
        return new LaunchRequest(source, "2025-2026", RunScope.fullSeason(), RunTrigger.MANUAL, "user-1", null);
    }

    @Test
    void queuesTheRunThenDispatchesIt() {
        PipelineRun run = launcher.launch(request(PipelineSource.RFETM));

        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(runs.findById(run.id())).isPresent();
        assertThat(dispatcher.dispatched).containsExactly(run.id());
    }

    @Test
    void conflictPropagatesAndNothingIsDispatched() {
        launcher.launch(request(PipelineSource.RFETM));
        dispatcher.dispatched.clear();

        assertThatThrownBy(() -> launcher.launch(request(PipelineSource.RFETM)))
                .isInstanceOf(ActiveRunConflictException.class);
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void failedDispatchFailsTheQueuedRunAndRethrows() {
        IllegalStateException boom = new IllegalStateException("pool is full");
        dispatcher.failWith(boom);

        assertThatThrownBy(() -> launcher.launch(request(PipelineSource.FCTT))).isSameAs(boom);

        PipelineRun stored = runs.findByStatusIn(java.util.Set.of(RunStatus.FAILED)).get(0);
        assertThat(stored.error().code()).isEqualTo("DISPATCH_FAILED");
        assertThat(stored.error().message()).contains("pool is full");
        assertThat(runs.findActiveBySource(PipelineSource.FCTT)).isEmpty();
    }
}
