package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunLauncher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryPipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryRunUnitRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingObserver;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitPlanner;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.junit.jupiter.api.Test;

class RetryUnitTest {

    private static final ScopeFilter G1 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
    private static final ScopeFilter G2 = new ScopeFilter("SENIOR", "G2", null, null, null, List.of(3));
    private static final RunError ERROR = new RunError("INGEST_FAILED", "boom");

    private final InMemoryPipelineRunRepository runs = new InMemoryPipelineRunRepository();
    private final InMemoryRunUnitRepository units = new InMemoryRunUnitRepository(runs);
    private final RecordingDispatcher dispatcher = new RecordingDispatcher();
    private final FakeRunClock clock = new FakeRunClock();
    private final RetryUnit retry =
            new RetryUnit(runs, units, new RunLauncher(runs, dispatcher, clock, new RecordingObserver()));

    /** A PARTIAL run whose first unit succeeded and whose second failed. */
    private PipelineRun partialRun(boolean force) {
        return partialRun(runs, units, force);
    }

    private PipelineRun partialRun(InMemoryPipelineRunRepository runRepo, InMemoryRunUnitRepository unitRepo,
            boolean force) {
        RunScope scope = new RunScope(List.of(G1, G2));
        PipelineRun queued = runRepo.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                scope, force, RunTrigger.MANUAL, "user", null, clock.now()));
        Instant at = clock.now();
        List<RunUnit> planned = unitRepo.addAll(UnitPlanner.plan(queued.id(), scope));
        unitRepo.update(planned.get(0).startIngest("a", at).noChanges(at));
        unitRepo.update(planned.get(1).startIngest("b", at).fail(ERROR, at));
        return runRepo.update(queued.start(at).finish(RunStatus.PARTIAL, null, at));
    }

    private RunUnit failedUnit(PipelineRun run) {
        return units.findByRunId(run.id()).get(1);
    }

    @Test
    void createsAQueuedUnitRetryRunForTheScopeOfTheUnitAndDispatchesIt() {
        PipelineRun original = partialRun(true);
        RunUnit unit = failedUnit(original);

        RetryUnit.Outcome outcome = retry.retry(original.id(), unit.id(), "operator");

        PipelineRun created = ((RetryUnit.Created) outcome).run();
        assertThat(created.trigger()).isEqualTo(RunTrigger.UNIT_RETRY);
        assertThat(created.retryOfRunId()).isEqualTo(original.id());
        assertThat(created.retryOfUnitId()).isEqualTo(unit.id());
        assertThat(created.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(created.requestedBy()).isEqualTo("operator");
        assertThat(created.source()).isEqualTo(PipelineSource.BCNESA);
        assertThat(created.season()).isEqualTo("2025-2026");
        assertThat(created.scope()).isEqualTo(unit.scope());
        assertThat(created.force()).isTrue();
        assertThat(dispatcher.dispatched).containsExactly(created.id());
        assertThat(runs.findRetriesOfUnit(unit.id())).extracting(PipelineRun::id).containsExactly(created.id());
    }

    @Test
    void theOriginalRunAndUnitStayUnchanged() {
        PipelineRun original = partialRun(false);
        RunUnit unit = failedUnit(original);

        retry.retry(original.id(), unit.id(), "operator");

        assertThat(runs.findById(original.id()).orElseThrow().status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(runs.findById(original.id()).orElseThrow().version()).isEqualTo(original.version());
        assertThat(units.findById(unit.id()).orElseThrow().status()).isEqualTo(UnitStatus.FAILED);
        assertThat(units.findById(unit.id()).orElseThrow().version()).isEqualTo(unit.version());
    }

    @Test
    void anUnknownRunOrUnitIsNotFound() {
        PipelineRun original = partialRun(false);

        assertThat(retry.retry(UUID.randomUUID(), failedUnit(original).id(), "o")).isInstanceOf(RetryUnit.NotFound.class);
        assertThat(retry.retry(original.id(), UUID.randomUUID(), "o")).isInstanceOf(RetryUnit.NotFound.class);
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void aUnitOfAnotherRunIsNotFound() {
        PipelineRun original = partialRun(false);
        PipelineRun other = partialRun(false);

        RetryUnit.Outcome outcome = retry.retry(original.id(), failedUnit(other).id(), "operator");

        assertThat(outcome).isInstanceOf(RetryUnit.NotFound.class);
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void aUnitThatDidNotFailIsNotRetryable() {
        PipelineRun original = partialRun(false);
        RunUnit succeeded = units.findByRunId(original.id()).get(0);

        RetryUnit.NotRetryable outcome =
                (RetryUnit.NotRetryable) retry.retry(original.id(), succeeded.id(), "operator");

        assertThat(outcome.code()).isEqualTo("UNIT_NOT_RETRYABLE");
        assertThat(outcome.message()).contains(succeeded.label());
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void anActiveRunOfTheSourceIsReportedAsRunActiveWithoutQueueing() {
        PipelineRun original = partialRun(false);
        PipelineRun active = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "user", null, clock.now()));

        RetryUnit.NotRetryable outcome =
                (RetryUnit.NotRetryable) retry.retry(original.id(), failedUnit(original).id(), "operator");

        assertThat(outcome.code()).isEqualTo("RUN_ACTIVE");
        assertThat(active.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(dispatcher.dispatched).isEmpty();
    }

    @Test
    void aSourceThatBecomesBusyBetweenTheCheckAndTheLaunchIsRejected() {
        AtomicInteger checks = new AtomicInteger();
        InMemoryPipelineRunRepository racing = new InMemoryPipelineRunRepository() {
            @Override
            public synchronized Optional<PipelineRun> findActiveBySource(PipelineSource source) {
                // the eligibility check sees no active run; the launch and the lookup after it do
                return checks.getAndIncrement() == 0 ? Optional.empty() : super.findActiveBySource(source);
            }
        };
        InMemoryRunUnitRepository racingUnits = new InMemoryRunUnitRepository(racing);
        PipelineRun original = partialRun(racing, racingUnits, false);
        PipelineRun active = racing.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "user", null, clock.now()));
        RetryUnit racer = new RetryUnit(racing, racingUnits,
                new RunLauncher(racing, dispatcher, clock, new RecordingObserver()));

        checks.set(0);

        RetryUnit.Outcome outcome = racer.retry(original.id(), racingUnits.findByRunId(original.id()).get(1).id(),
                "operator");

        assertThat(outcome).isInstanceOf(RetryUnit.Rejected.class);
        RetryUnit.Rejected rejected = (RetryUnit.Rejected) outcome;
        assertThat(rejected.code()).isEqualTo(RetryUnit.ACTIVE_RUN);
        assertThat(rejected.activeRunId()).isEqualTo(active.id());
        assertThat(dispatcher.dispatched).isEmpty();
    }
}
