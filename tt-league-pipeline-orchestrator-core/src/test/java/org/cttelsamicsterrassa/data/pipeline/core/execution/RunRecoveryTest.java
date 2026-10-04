package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RunRecoveryTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();

    private PipelineRun create(PipelineSource source, Instant createdAt) {
        return runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(),
                false, RunTrigger.MANUAL, "u", null, createdAt));
    }

    @Test
    void dispatchesOnlyActiveRunsOldestFirst() {
        PipelineRun newer = create(PipelineSource.RFETM, T0.plusSeconds(60));
        PipelineRun older = create(PipelineSource.BCNESA, T0);
        PipelineRun failed = create(PipelineSource.FCTT, T0.plusSeconds(30));
        runs.update(failed.fail(new RunError("X", "failed"), T0.plusSeconds(31)));

        int count = new RunRecovery(runs, dispatcher).recover();

        assertThat(count).isEqualTo(2);
        assertThat(dispatcher.dispatched).containsExactly(older.id(), newer.id());
    }

    @Test
    void doesNothingWithoutActiveRuns() {
        assertThat(new RunRecovery(runs, dispatcher).recover()).isZero();
        assertThat(dispatcher.dispatched).isEmpty();
    }
}
