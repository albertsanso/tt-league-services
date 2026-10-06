package org.cttelsamicsterrassa.data.pipeline.core.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingAlertRequests;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AlertRunObserverTest {

    private static final Instant T0 = Instant.parse("2026-10-05T10:00:00Z");

    private final RecordingAlertRequests requests = new RecordingAlertRequests();
    private final AlertRunObserver observer = new AlertRunObserver(requests);

    private static PipelineRun queued() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "ana", null, T0);
    }

    @Test
    void nonTerminalStatusesRequestNothing() {
        PipelineRun queued = queued();
        PipelineRun running = queued.start(T0.plusSeconds(1));

        observer.runChanged(queued);
        observer.runChanged(running);

        assertThat(requests.count()).isZero();
    }

    @Test
    void everyTerminalStatusRequestsOneEvaluation() {
        PipelineRun running = queued().start(T0.plusSeconds(1));

        observer.runChanged(running.finish(RunStatus.NO_CHANGES, null, T0.plusSeconds(10)));
        observer.runChanged(running.finish(RunStatus.SUCCEEDED, null, T0.plusSeconds(11)));
        observer.runChanged(running.finish(RunStatus.PARTIAL, null, T0.plusSeconds(12)));
        observer.runChanged(running.fail(new RunError("IMPORT_FAILED", "boom"), T0.plusSeconds(13)));

        assertThat(requests.count()).isEqualTo(4);
    }

    @Test
    void stepChangesAreIgnored() {
        observer.stepChanged(null);

        assertThat(requests.count()).isZero();
    }
}
