package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.CompositeRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.junit.jupiter.api.Test;

class CompositeRunObserverTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    private static PipelineRun run() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026", RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "user", null, T0);
    }

    private static final class Throwing implements RunObserver {

        @Override
        public void runChanged(PipelineRun run) {
            throw new IllegalStateException("boom");
        }

        @Override
        public void stepChanged(PipelineStep step) {
            throw new IllegalStateException("boom");
        }

        @Override
        public void unitChanged(RunUnit unit) {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void callsEveryObserverInOrder() {
        List<String> order = new ArrayList<>();
        RunObserver first = new RunObserver() {
            @Override
            public void runChanged(PipelineRun run) {
                order.add("first");
            }
        };
        RunObserver second = new RunObserver() {
            @Override
            public void runChanged(PipelineRun run) {
                order.add("second");
            }
        };

        CompositeRunObserver.of(List.of(first, second)).runChanged(run());

        assertThat(order).containsExactly("first", "second");
    }

    @Test
    void aFailingObserverDoesNotStopTheOthersOrReachTheCaller() {
        RecordingObserver recording = new RecordingObserver();
        CompositeRunObserver composite = CompositeRunObserver.of(List.of(new Throwing(), recording));
        PipelineRun run = run();

        composite.runChanged(run);
        composite.stepChanged(PipelineStep.start(UUID.randomUUID(), run.id(), UUID.randomUUID(), StepKind.INGEST, 1, T0, null));

        assertThat(recording.events).containsExactly("run:QUEUED", "step:INGEST/1:RUNNING");
    }

    @Test
    void unitChangesAreForwardedInOrderAndAFailingObserverIsContained() {
        RecordingObserver recording = new RecordingObserver();
        CompositeRunObserver composite = CompositeRunObserver.of(List.of(new Throwing(), recording));
        RunUnit unit = RunUnit.plan(UUID.randomUUID(), UUID.randomUUID(), 3, UnitKey.SEASON, "Full season",
                RunScope.fullSeason());

        composite.unitChanged(unit);

        assertThat(recording.unitEvents).containsExactly("unit:3:PENDING");
    }
}
