package org.cttelsamicsterrassa.data.pipeline.core.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingAlertRequests;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
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
        PipelineRun ingest = queued.startIngest("abc", T0.plusSeconds(1));

        observer.runChanged(queued);
        observer.runChanged(ingest);
        observer.runChanged(ingest.packed(T0.plusSeconds(2)));

        assertThat(requests.count()).isZero();
    }

    @Test
    void everyTerminalStatusRequestsOneEvaluation() {
        PipelineRun ingest = queued().startIngest("abc", T0.plusSeconds(1));
        PipelineRun importing = ingest.packed(T0.plusSeconds(2)).startImport(UUID.randomUUID(), T0.plusSeconds(3));

        observer.runChanged(ingest.noChanges(T0.plusSeconds(10)));
        observer.runChanged(importing.succeed(T0.plusSeconds(11)));
        observer.runChanged(importing.partial(T0.plusSeconds(12)));
        observer.runChanged(importing.fail(new RunError("IMPORT_FAILED", "boom"), T0.plusSeconds(13)));

        assertThat(requests.count()).isEqualTo(4);
    }

    @Test
    void stepChangesAreIgnored() {
        observer.stepChanged(null);

        assertThat(requests.count()).isZero();
    }
}
