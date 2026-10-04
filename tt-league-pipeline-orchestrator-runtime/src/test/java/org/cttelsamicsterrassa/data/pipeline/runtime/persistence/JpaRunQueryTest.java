package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunPage;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** {@code force}, run listing and the batched step lookup. */
class JpaRunQueryTest extends AbstractPersistenceTest {

    @Autowired
    PipelineRunRepository runs;

    @Autowired
    PipelineStepRepository steps;

    private PipelineRun create(PipelineSource source, boolean force, int seconds, boolean fail) {
        PipelineRun run = runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026",
                RunScope.fullSeason(), force, RunTrigger.MANUAL, "alice", null, T0.plusSeconds(seconds)));
        return fail ? runs.update(run.fail(new RunError("E", "boom"), T0.plusSeconds(seconds + 1))) : run;
    }

    private static RunQuery query(Set<PipelineSource> sources, Set<RunStatus> statuses, Instant from, Instant to,
            int page, int size) {
        return new RunQuery(sources, statuses, from, to, page, size);
    }

    @Test
    void forceRoundTrips() {
        PipelineRun forced = create(PipelineSource.RFETM, true, 0, false);

        assertThat(runs.findById(forced.id()).orElseThrow().force()).isTrue();
        assertThat(create(PipelineSource.BCNESA, false, 0, false).force()).isFalse();
    }

    @Test
    void filtersBySourceStatusAndCreationRange() {
        PipelineRun oldFailed = create(PipelineSource.RFETM, false, 0, true);
        PipelineRun newFailed = create(PipelineSource.RFETM, false, 100, true);
        PipelineRun otherSource = create(PipelineSource.FCTT, false, 50, true);
        PipelineRun active = create(PipelineSource.BCNESA, false, 60, false);

        assertThat(ids(runs.find(query(Set.of(PipelineSource.RFETM), Set.of(), null, null, 0, 20))))
                .containsExactly(newFailed.id(), oldFailed.id());
        assertThat(ids(runs.find(query(Set.of(), Set.of(RunStatus.QUEUED), null, null, 0, 20))))
                .containsExactly(active.id());
        assertThat(ids(runs.find(query(Set.of(), Set.of(RunStatus.FAILED), T0.plusSeconds(50), T0.plusSeconds(100),
                0, 20)))).containsExactly(otherSource.id());
    }

    @Test
    void pagesNewestFirstWithTotals() {
        PipelineRun a = create(PipelineSource.RFETM, false, 0, true);
        PipelineRun b = create(PipelineSource.RFETM, false, 10, true);
        PipelineRun c = create(PipelineSource.RFETM, false, 20, true);

        RunPage first = runs.find(query(Set.of(), Set.of(), null, null, 0, 2));
        RunPage second = runs.find(query(Set.of(), Set.of(), null, null, 1, 2));

        assertThat(ids(first)).containsExactly(c.id(), b.id());
        assertThat(ids(second)).containsExactly(a.id());
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
    }

    @Test
    void findsStepsOfSeveralRunsInOneCall() {
        PipelineRun one = create(PipelineSource.RFETM, false, 0, true);
        PipelineRun two = create(PipelineSource.FCTT, false, 0, true);
        PipelineRun none = create(PipelineSource.BCNESA, false, 0, true);
        PipelineStep late = PipelineStep.start(UUID.randomUUID(), one.id(), StepKind.FETCH_PACKAGE, 1,
                T0.plusSeconds(10), null);
        PipelineStep early = PipelineStep.start(UUID.randomUUID(), one.id(), StepKind.INGEST, 1, T0, null);
        PipelineStep other = PipelineStep.start(UUID.randomUUID(), two.id(), StepKind.INGEST, 1, T0, null);
        steps.save(late);
        steps.save(early);
        steps.save(other);

        Map<UUID, List<PipelineStep>> byRun = steps.findByRunIds(List.of(one.id(), two.id(), none.id()));

        assertThat(byRun).containsOnlyKeys(one.id(), two.id());
        assertThat(byRun.get(one.id())).extracting(PipelineStep::id).containsExactly(early.id(), late.id());
        assertThat(steps.findByRunIds(List.of())).isEmpty();
    }

    private static List<UUID> ids(RunPage page) {
        return page.items().stream().map(PipelineRun::id).toList();
    }
}
