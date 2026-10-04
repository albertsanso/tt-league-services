package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JpaPipelineRunRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    PipelineRunRepository runs;

    @Test
    void roundTripsEveryField() {
        RunScope scope = new RunScope(List.of(
                new ScopeFilter("DH", "A", "1", "BCN", "M", List.of(3, 4)),
                new ScopeFilter(null, null, null, null, "F", List.of())));
        PipelineRun original = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                scope, RunTrigger.MANUAL, "user-1", null, T0));
        PipelineRun retry = PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2025-2026", scope,
                RunTrigger.RETRY, "user-2", original.id(), T0);
        runs.update(original.fail(new RunError("E", "boom"), T0.plusSeconds(1)));

        PipelineRun stored = runs.create(retry);
        PipelineRun loaded = runs.findById(retry.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(stored);
        assertThat(loaded.scope()).isEqualTo(scope);
        assertThat(loaded.retryOfRunId()).isEqualTo(original.id());
        assertThat(loaded.trigger()).isEqualTo(RunTrigger.RETRY);
        assertThat(loaded.requestedBy()).isEqualTo("user-2");
        assertThat(loaded.createdAt()).isEqualTo(T0);
    }

    @Test
    void persistsLifecycleStepByStepIncrementingVersion() {
        UUID job = UUID.randomUUID();
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        assertThat(run.version()).isZero();

        run = runs.update(run.startIngest("abc123", T0.plusSeconds(1)));
        assertThat(run.version()).isEqualTo(1);
        assertThat(run.status()).isEqualTo(RunStatus.RUNNING_INGEST);
        assertThat(run.ingestRunId()).isEqualTo("abc123");

        run = runs.update(run.packed(T0.plusSeconds(2)));
        assertThat(run.version()).isEqualTo(2);

        run = runs.update(run.startImport(job, T0.plusSeconds(3)));
        assertThat(run.version()).isEqualTo(3);
        assertThat(run.importJobId()).isEqualTo(job);

        run = runs.update(run.partial(T0.plusSeconds(4)));
        assertThat(run.version()).isEqualTo(4);

        PipelineRun loaded = runs.findById(run.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(loaded.finishedAt()).isEqualTo(T0.plusSeconds(4));
        assertThat(loaded.version()).isEqualTo(4);
    }

    @Test
    void failedRunKeepsItsError() {
        PipelineRun run = runs.create(queued(PipelineSource.RFETM));
        runs.update(run.fail(new RunError("INGEST_FAILED", "network down"), T0.plusSeconds(1)));

        PipelineRun loaded = runs.findById(run.id()).orElseThrow();
        assertThat(loaded.error()).isEqualTo(new RunError("INGEST_FAILED", "network down"));
        assertThat(loaded.startedAt()).isNull();
    }

    @Test
    void staleVersionIsRejected() {
        PipelineRun created = runs.create(queued(PipelineSource.RFETM));
        runs.update(created.startIngest("abc", T0.plusSeconds(1)));

        assertThatThrownBy(() -> runs.update(created.fail(new RunError("E", "m"), T0.plusSeconds(2))))
                .isInstanceOf(StaleRunException.class);
    }

    @Test
    void unknownRunIsRejected() {
        assertThatThrownBy(() -> runs.update(queued(PipelineSource.RFETM)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findsActiveRunAndOrdersByStatusOldestFirst() {
        PipelineRun older = runs.create(queued(PipelineSource.RFETM));
        PipelineRun newer = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                RunScope.fullSeason(), RunTrigger.SCHEDULED, "system:scheduler", null, T0.plusSeconds(60)));

        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).map(PipelineRun::id).contains(older.id());
        assertThat(runs.findActiveBySource(PipelineSource.FCTT)).isEmpty();
        assertThat(runs.findByStatusIn(Set.of(RunStatus.QUEUED))).extracting(PipelineRun::id)
                .containsExactly(older.id(), newer.id());
        assertThat(runs.findByStatusIn(Set.of(RunStatus.FAILED))).isEmpty();
        assertThat(runs.findByStatusIn(Set.of())).isEmpty();
    }
}
