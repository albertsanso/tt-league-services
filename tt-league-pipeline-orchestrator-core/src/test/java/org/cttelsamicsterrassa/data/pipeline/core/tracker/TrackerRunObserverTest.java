package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.junit.jupiter.api.Test;

class TrackerRunObserverTest {

    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    private record Request(PipelineSource source, String season, RunRef run) {
    }

    private final List<Request> requests = new ArrayList<>();
    private final TrackerRunObserver observer =
            new TrackerRunObserver((source, season, run) -> requests.add(new Request(source, season, run)));

    private static PipelineRun queued() {
        return PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", RunScope.fullSeason(), false,
                RunTrigger.MANUAL, "ana", null, T0);
    }

    @Test
    void nonTerminalStatusesRequestNothing() {
        PipelineRun queued = queued();
        PipelineRun ingest = queued.startIngest("abc", T0.plusSeconds(1));
        PipelineRun packed = ingest.packed(T0.plusSeconds(2));
        PipelineRun importing = packed.startImport(UUID.randomUUID(), T0.plusSeconds(3));

        for (PipelineRun run : List.of(queued, ingest, packed, importing)) {
            observer.runChanged(run);
        }

        assertThat(requests).isEmpty();
    }

    @Test
    void everyTerminalStatusRequestsARecomputeWithTheRunReference() {
        PipelineRun ingest = queued().startIngest("abc", T0.plusSeconds(1));
        PipelineRun importing = ingest.packed(T0.plusSeconds(2)).startImport(UUID.randomUUID(), T0.plusSeconds(3));
        List<PipelineRun> terminal = List.of(
                ingest.noChanges(T0.plusSeconds(10)),
                importing.succeed(T0.plusSeconds(11)),
                importing.partial(T0.plusSeconds(12)),
                importing.fail(new RunError("IMPORT_FAILED", "boom"), T0.plusSeconds(13)));

        terminal.forEach(observer::runChanged);

        assertThat(requests).hasSize(4);
        for (int i = 0; i < terminal.size(); i++) {
            PipelineRun run = terminal.get(i);
            assertThat(requests.get(i)).isEqualTo(
                    new Request(PipelineSource.FCTT, "2026-2027", new RunRef(run.id(), run.finishedAt())));
        }
    }

    @Test
    void stepChangesAreIgnored() {
        observer.stepChanged(null);

        assertThat(requests).isEmpty();
    }
}
