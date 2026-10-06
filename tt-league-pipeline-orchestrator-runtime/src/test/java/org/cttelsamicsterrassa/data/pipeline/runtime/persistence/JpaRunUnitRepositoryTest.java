package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitPlanner;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitProgress;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.StaleRunException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class JpaRunUnitRepositoryTest extends AbstractPersistenceTest {

    private static final ScopeFilter G1 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
    private static final ScopeFilter G2 = new ScopeFilter("SENIOR", "G2", "1a Fase", "BARCELONA", "M", List.of(3, 4));
    private static final ScopeFilter G3 = new ScopeFilter("JUNIOR", "G1", null, null, null, List.of());

    @Autowired
    PipelineRunRepository runs;

    private PipelineRun run(PipelineSource source) {
        return runs.create(queued(source));
    }

    @Test
    void roundTripsEveryFieldOfAPlannedUnit() {
        PipelineRun run = run(PipelineSource.BCNESA);
        List<RunUnit> planned = UnitPlanner.plan(run.id(), new RunScope(List.of(G2)));

        List<RunUnit> stored = unitRepo.addAll(planned);

        RunUnit loaded = unitRepo.findById(planned.get(0).id()).orElseThrow();
        assertThat(loaded).usingRecursiveComparison().isEqualTo(stored.get(0));
        assertThat(loaded.status()).isEqualTo(UnitStatus.PENDING);
        assertThat(loaded.scope().filters()).containsExactly(G2);
        assertThat(loaded.unitKey()).isEqualTo(UnitKey.of(G2));
        assertThat(loaded.label()).isEqualTo(planned.get(0).label());
        assertThat(loaded.version()).isZero();
        assertThat(loaded.progress()).isNull();
    }

    @Test
    void aFullSeasonUnitKeepsTheSeasonKeyAndAnEmptyScope() {
        PipelineRun run = run(PipelineSource.RFETM);

        RunUnit stored = unit(run);

        RunUnit loaded = unitRepo.findById(stored.id()).orElseThrow();
        assertThat(loaded.unitKey()).isEqualTo(UnitKey.SEASON);
        assertThat(loaded.scope().isFullSeason()).isTrue();
        assertThat(loaded.label()).isEqualTo("Full season");
    }

    @Test
    void unitsOfARunComeBackInOrdinalOrderEvenWhenInsertedInAnotherOrder() {
        PipelineRun run = run(PipelineSource.BCNESA);
        List<RunUnit> planned = UnitPlanner.plan(run.id(), new RunScope(List.of(G1, G2, G3)));

        unitRepo.addAll(List.of(planned.get(2), planned.get(0), planned.get(1)));

        assertThat(unitRepo.findByRunId(run.id())).extracting(RunUnit::ordinal).containsExactly(0, 1, 2);
        assertThat(unitRepo.findByRunId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void anOrdinalIsUniquePerRunAndAFailedBatchLeavesNothingBehind() {
        PipelineRun run = run(PipelineSource.BCNESA);
        List<RunUnit> planned = UnitPlanner.plan(run.id(), new RunScope(List.of(G1, G2)));
        RunUnit clash = RunUnit.plan(UUID.randomUUID(), run.id(), 1, UnitKey.of(G3), "JUNIOR G1",
                new RunScope(List.of(G3)));

        assertThatThrownBy(() -> unitRepo.addAll(List.of(planned.get(0), planned.get(1), clash)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(unitRepo.findByRunId(run.id())).isEmpty();
    }

    @Test
    void theLifecycleIncrementsTheVersionAndRoundTripsTimingsIdsAndErrors() {
        PipelineRun run = run(PipelineSource.RFETM);
        UUID job = UUID.randomUUID();
        RunUnit unit = unit(run);

        unit = unitRepo.update(unit.startIngest("abc123", T0.plusSeconds(1)));
        assertThat(unit.version()).isEqualTo(1);
        assertThat(unit.status()).isEqualTo(UnitStatus.RUNNING_INGEST);
        assertThat(unit.ingestRunId()).isEqualTo("abc123");
        assertThat(unit.startedAt()).isEqualTo(T0.plusSeconds(1));

        unit = unitRepo.update(unit.packed(T0.plusSeconds(2)));
        unit = unitRepo.update(unit.startImport(job, T0.plusSeconds(3)));
        assertThat(unit.importJobId()).isEqualTo(job);
        unit = unitRepo.update(unit.partial(T0.plusSeconds(4)));
        assertThat(unit.version()).isEqualTo(4);

        RunUnit loaded = unitRepo.findById(unit.id()).orElseThrow();
        assertThat(loaded.status()).isEqualTo(UnitStatus.PARTIAL);
        assertThat(loaded.finishedAt()).isEqualTo(T0.plusSeconds(4));
        assertThat(loaded.importJobId()).isEqualTo(job);
        assertThat(loaded.version()).isEqualTo(4);
    }

    @Test
    void aFailedUnitKeepsItsErrorAndASkippedOneNeverStarted() {
        PipelineRun run = run(PipelineSource.BCNESA);
        List<RunUnit> units = units(run, G1, G2);

        unitRepo.update(units.get(0).startIngest("i", T0).fail(new RunError("INGEST_FAILED", "network down"),
                T0.plusSeconds(5)));
        unitRepo.update(units.get(1).skip(new RunError("UNIT_SKIPPED", "skipped after INGEST_FAILED on unit X"),
                T0.plusSeconds(6)));

        RunUnit failed = unitRepo.findById(units.get(0).id()).orElseThrow();
        assertThat(failed.error()).isEqualTo(new RunError("INGEST_FAILED", "network down"));
        assertThat(failed.startedAt()).isEqualTo(T0);
        RunUnit skipped = unitRepo.findById(units.get(1).id()).orElseThrow();
        assertThat(skipped.status()).isEqualTo(UnitStatus.SKIPPED);
        assertThat(skipped.startedAt()).isNull();
        assertThat(skipped.error().code()).isEqualTo("UNIT_SKIPPED");
    }

    @Test
    void progressRoundTripsWhileRunningAndIsClearedByTheNextTransition() {
        PipelineRun run = run(PipelineSource.BCNESA);
        RunUnit running = unitRepo.update(units(run, G1).get(0).startIngest("i", T0));

        RunUnit withProgress = unitRepo.update(running.withProgress(
                new UnitProgress(StepKind.INGEST, "DOWNLOAD", 3, 12L, "league three", T0.plusSeconds(7))));

        RunUnit loaded = unitRepo.findById(running.id()).orElseThrow();
        assertThat(loaded.progress()).isEqualTo(
                new UnitProgress(StepKind.INGEST, "DOWNLOAD", 3, 12L, "league three", T0.plusSeconds(7)));
        assertThat(withProgress.version()).isEqualTo(running.version() + 1);

        RunUnit unknownTotal = unitRepo.update(loaded.withProgress(
                new UnitProgress(StepKind.FETCH_PACKAGE, null, 0, null, null, T0.plusSeconds(8))));
        assertThat(unitRepo.findById(running.id()).orElseThrow().progress())
                .isEqualTo(new UnitProgress(StepKind.FETCH_PACKAGE, null, 0, null, null, T0.plusSeconds(8)));

        unitRepo.update(unknownTotal.noChanges(T0.plusSeconds(9)));
        assertThat(unitRepo.findById(running.id()).orElseThrow().progress()).isNull();
    }

    @Test
    void aStaleVersionIsRejected() {
        PipelineRun run = run(PipelineSource.BCNESA);
        RunUnit created = units(run, G1).get(0);
        unitRepo.update(created.startIngest("i", T0));

        assertThatThrownBy(() -> unitRepo.update(created.fail(new RunError("E", "m"), T0.plusSeconds(1))))
                .isInstanceOf(StaleRunException.class)
                .hasMessageContaining(created.id().toString());
    }

    @Test
    void findsTheUnitsOfSeveralRunsInOneCall() {
        PipelineRun one = run(PipelineSource.RFETM);
        PipelineRun two = run(PipelineSource.FCTT);
        PipelineRun none = run(PipelineSource.BCNESA);
        List<RunUnit> oneUnits = units(one, G1, G2);
        RunUnit twoUnit = unit(two);

        Map<UUID, List<RunUnit>> byRun = unitRepo.findByRunIds(List.of(one.id(), two.id(), none.id()));

        assertThat(byRun).containsOnlyKeys(one.id(), two.id());
        assertThat(byRun.get(one.id())).extracting(RunUnit::id)
                .containsExactly(oneUnits.get(0).id(), oneUnits.get(1).id());
        assertThat(byRun.get(two.id())).extracting(RunUnit::id).containsExactly(twoUnit.id());
        assertThat(unitRepo.findByRunIds(List.of())).isEmpty();
    }

    // ----------------------------------------------- newest finished units per key

    private RunUnit finished(PipelineSource source, ScopeFilter filter, UnitStatus status, int seconds) {
        PipelineRun run = runs.create(PipelineRun.queue(UUID.randomUUID(), source, "2025-2026",
                new RunScope(List.of(filter)), false, RunTrigger.MANUAL, "u", null, T0.plusSeconds(seconds)));
        RunUnit unit = units(run, filter).get(0);
        Instant start = T0.plusSeconds(seconds);
        Instant end = T0.plusSeconds(seconds + 10);
        RunUnit done = switch (status) {
            case SKIPPED -> unit.skip(new RunError("UNIT_SKIPPED", "skipped"), end);
            case FAILED -> unit.startIngest("i", start).fail(new RunError("INGEST_FAILED", "boom"), end);
            case NO_CHANGES -> unit.startIngest("i", start).noChanges(end);
            case SUCCEEDED -> unit.startIngest("i", start).packed(start).startImport(UUID.randomUUID(), start)
                    .succeed(end);
            default -> throw new IllegalArgumentException(status.toString());
        };
        runs.update(run.start(T0.plusSeconds(seconds)).finish(RunStatus.PARTIAL, null, T0.plusSeconds(seconds + 11)));
        return unitRepo.update(done);
    }

    @Test
    void theNewestFinishedUnitsPerKeyAreFoundNewestFirstAndLimited() {
        RunUnit oldest = finished(PipelineSource.BCNESA, G1, UnitStatus.FAILED, 0);
        RunUnit middle = finished(PipelineSource.BCNESA, G1, UnitStatus.SUCCEEDED, 100);
        RunUnit newest = finished(PipelineSource.BCNESA, G1, UnitStatus.FAILED, 200);
        RunUnit otherKey = finished(PipelineSource.BCNESA, G2, UnitStatus.NO_CHANGES, 50);

        Map<String, List<RunUnit>> newestTwo = unitRepo.findNewestFinishedBySource(PipelineSource.BCNESA, 2, Set.of());

        assertThat(newestTwo).containsOnlyKeys(UnitKey.of(G1), UnitKey.of(G2));
        assertThat(newestTwo.get(UnitKey.of(G1))).extracting(RunUnit::id).containsExactly(newest.id(), middle.id());
        assertThat(newestTwo.get(UnitKey.of(G2))).extracting(RunUnit::id).containsExactly(otherKey.id());
        assertThat(unitRepo.findNewestFinishedBySource(PipelineSource.BCNESA, 3, Set.of()).get(UnitKey.of(G1)))
                .extracting(RunUnit::id).containsExactly(newest.id(), middle.id(), oldest.id());
    }

    @Test
    void skippedAndActiveUnitsAndOtherSourcesAreNotCounted() {
        RunUnit failed = finished(PipelineSource.BCNESA, G1, UnitStatus.FAILED, 0);
        finished(PipelineSource.BCNESA, G1, UnitStatus.SKIPPED, 100);
        finished(PipelineSource.FCTT, G1, UnitStatus.FAILED, 200);
        PipelineRun active = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA, "2025-2026",
                new RunScope(List.of(G1)), false, RunTrigger.MANUAL, "u", null, T0.plusSeconds(300)));
        unitRepo.update(units(active, G1).get(0).startIngest("i", T0.plusSeconds(300)));

        Map<String, List<RunUnit>> found = unitRepo.findNewestFinishedBySource(PipelineSource.BCNESA, 2, Set.of());

        assertThat(found.get(UnitKey.of(G1))).extracting(RunUnit::id).containsExactly(failed.id());
        assertThat(unitRepo.findNewestFinishedBySource(PipelineSource.RFETM, 2, Set.of())).isEmpty();
    }

    @Test
    void theKeyFilterLimitsTheResult() {
        finished(PipelineSource.BCNESA, G1, UnitStatus.FAILED, 0);
        finished(PipelineSource.BCNESA, G2, UnitStatus.FAILED, 10);
        finished(PipelineSource.BCNESA, G3, UnitStatus.FAILED, 20);

        Map<String, List<RunUnit>> found =
                unitRepo.findNewestFinishedBySource(PipelineSource.BCNESA, 2, Set.of(UnitKey.of(G1), UnitKey.of(G3)));

        assertThat(found).containsOnlyKeys(UnitKey.of(G1), UnitKey.of(G3));
        assertThatThrownBy(() -> unitRepo.findNewestFinishedBySource(PipelineSource.BCNESA, 0, Set.of()))
                .isInstanceOf(RuntimeException.class);
    }
}
