package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.counters;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.created;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.job;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.season;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.failed;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.finished;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.running;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.runningWithProgress;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestMode;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestProgress;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ExecutorHarness;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.PackageResponse;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitProgress;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.junit.jupiter.api.Test;

/** Runs split into units: sequential execution, per-unit failure isolation and the derived run status. */
class RunExecutorUnitsTest {

    private static final byte[] ZIP = "zip-content".getBytes(StandardCharsets.UTF_8);

    private static final ScopeFilter G1 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
    private static final ScopeFilter G2 = new ScopeFilter("SENIOR", "G2", null, null, null, List.of(3));
    private static final ScopeFilter G3 = new ScopeFilter("JUNIOR", "G1", null, null, null, List.of(2, 3));
    private static final RunScope THREE = new RunScope(List.of(G1, G2, G3));

    private final ScriptedIngestGateway ingest = new ScriptedIngestGateway();
    private final ScriptedImportGateway platform = new ScriptedImportGateway();
    private final InMemoryArtifactStore store = new InMemoryArtifactStore();
    private final ExecutorHarness h = new ExecutorHarness(ingest, platform, store);

    private PipelineRun execute(PipelineRun run) {
        h.executor.execute(run.id());
        return h.run(run.id());
    }

    private List<RunUnit> units(PipelineRun run) {
        return h.units.findByRunId(run.id());
    }

    private List<PipelineStep> steps(RunUnit unit, StepKind kind) {
        return h.steps.findByRunId(unit.runId()).stream()
                .filter(step -> step.unitId().equals(unit.id()) && step.kind() == kind)
                .toList();
    }

    private void scriptPackedUnit(String ingestRunId, UUID jobId) {
        ingest.start(ingestRunId).poll(finished(ingestRunId, "SUCCEEDED", true))
                .packageResponse(PackageResponse.valid(ZIP));
        platform.submit(created(jobId)).poll(job(jobId, "SUCCEEDED", null,
                season("2025-2026", "SUCCEEDED", counters(1, 1))));
    }

    @Test
    void everyUnitRunsItsOwnChainSequentiallyInOrdinalOrder() {
        UUID job0 = UUID.randomUUID();
        UUID job1 = UUID.randomUUID();
        UUID job2 = UUID.randomUUID();
        scriptPackedUnit("ing0", job0);
        scriptPackedUnit("ing1", job1);
        scriptPackedUnit("ing2", job2);

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(units(run)).extracting(RunUnit::status).containsOnly(UnitStatus.SUCCEEDED);
        assertThat(units(run)).extracting(RunUnit::ordinal).containsExactly(0, 1, 2);
        assertThat(units(run)).extracting(RunUnit::ingestRunId).containsExactly("ing0", "ing1", "ing2");
        assertThat(units(run)).extracting(RunUnit::importJobId).containsExactly(job0, job1, job2);
        assertThat(ingest.startRequests).extracting(request -> request.scope().filters())
                .containsExactly(List.of(G1), List.of(G2), List.of(G3));
        assertThat(ingest.startRequests).extracting(request -> request.mode()).containsOnly(IngestMode.DELTA);
        assertThat(ingest.startRequests).extracting(request -> request.correlationId()).containsOnly(run.id());
        assertThat(h.reports.findByRunId(run.id())).hasSize(3);
    }

    @Test
    void eachUnitUsesItsOwnStorageFolderFileNameAndStepNumbering() {
        scriptPackedUnit("ing0", UUID.randomUUID());
        scriptPackedUnit("ing1", UUID.randomUUID());

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1, G2))));

        List<RunUnit> units = units(run);
        for (int i = 0; i < 2; i++) {
            RunUnit unit = units.get(i);
            String key = "bcnesa/2025-2026/" + run.id() + "/" + i + "-" + unit.unitKey().substring(0, 12)
                    + "/ingest-ing" + i + ".zip";
            assertThat(h.artifactRows.findByUnitId(unit.id())).singleElement()
                    .satisfies(artifact -> assertThat(artifact.storageKey()).isEqualTo(key));
            assertThat(store.exists(key)).isTrue();
            assertThat(steps(unit, StepKind.INGEST)).extracting(PipelineStep::attempt).containsExactly(1);
            assertThat(steps(unit, StepKind.IMPORT)).extracting(PipelineStep::attempt).containsExactly(1);
        }
        assertThat(platform.submissions).extracting(sent -> sent.fileName())
                .containsExactly(run.id() + "-0.zip", run.id() + "-1.zip");
        assertThat(platform.submissions).extracting(sent -> sent.clientRunId()).containsOnly(run.id());
    }

    @Test
    void aFullSeasonRunIsOneSnapshotUnit() {
        ingest.start("ing0").poll(finished("ing0", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(units(run)).singleElement().satisfies(unit -> {
            assertThat(unit.unitKey()).isEqualTo(UnitKey.SEASON);
            assertThat(unit.label()).isEqualTo("Full season");
            assertThat(unit.status()).isEqualTo(UnitStatus.NO_CHANGES);
        });
        assertThat(ingest.startRequests.get(0).mode()).isEqualTo(IngestMode.SNAPSHOT);
    }

    @Test
    void filtersSharingAnIdentityRunAsOneUnitWithTheMergedMatchDays() {
        ingest.start("ing0").poll(finished("ing0", "NO_CHANGES", false));
        RunScope scope = new RunScope(List.of(
                new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3)),
                new ScopeFilter("SENIOR", "G1", null, null, null, List.of(4))));

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, scope));

        assertThat(units(run)).hasSize(1);
        assertThat(ingest.startRequests.get(0).scope().filters())
                .singleElement().satisfies(filter -> assertThat(filter.matchDays()).containsExactly(3, 4));
    }

    @Test
    void oneFailingUnitDoesNotHideTheOthersAndTheRunIsPartial() {
        scriptPackedUnit("ing0", UUID.randomUUID());
        ingest.start("ing1").poll(failed("ing1", "FAILED", "parser exploded"));
        scriptPackedUnit("ing2", UUID.randomUUID());

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(run.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(run.error()).isNull();
        assertThat(units(run)).extracting(RunUnit::status)
                .containsExactly(UnitStatus.SUCCEEDED, UnitStatus.FAILED, UnitStatus.SUCCEEDED);
        assertThat(units(run).get(1).error().code()).isEqualTo("INGEST_FAILED");
        assertThat(units(run).get(1).error().message()).isEqualTo("parser exploded");
        assertThat(h.observer.events).contains("run:PARTIAL");
    }

    @Test
    void allUnitsFailingFailsTheRunWithTheFirstCodeAndTheList() {
        ingest.start("ing0").poll(failed("ing0", "FAILED", "boom"));
        ingest.start("ing1").poll(failed("ing1", "FAILED", "boom"));
        ingest.start("ing2").poll(finished("ing2", "SUCCEEDED", false));

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INGEST_FAILED");
        assertThat(run.error().message()).isEqualTo("3 of 3 units failed: INGEST_FAILED, INGEST_NO_PACKAGE");
    }

    @Test
    void everyUnitWithoutChangesIsANoChangesRun() {
        ingest.start("ing0").poll(finished("ing0", "NO_CHANGES", false));
        ingest.start("ing1").poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1, G2))));

        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
    }

    @Test
    void aUnitWithoutChangesNextToAnImportedOneSucceedsTheRun() {
        ingest.start("ing0").poll(finished("ing0", "NO_CHANGES", false));
        scriptPackedUnit("ing1", UUID.randomUUID());

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1, G2))));

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
    }

    @Test
    void aPartialImportMakesTheRunPartial() {
        UUID jobId = UUID.randomUUID();
        ingest.start("ing0").poll(finished("ing0", "SUCCEEDED", true)).packageResponse(PackageResponse.valid(ZIP));
        platform.submit(created(jobId)).poll(job(jobId, "PARTIAL", null, season("2025-2026", "FAILED", null)));
        scriptPackedUnit("ing1", UUID.randomUUID());

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1, G2))));

        assertThat(run.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(units(run)).extracting(RunUnit::status).containsExactly(UnitStatus.PARTIAL, UnitStatus.SUCCEEDED);
    }

    @Test
    void aTimedOutUnitFailsAloneAndTheNextUnitGetsItsOwnDeadline() {
        ExecutionSettings settings = new ExecutionSettings(ExecutorHarness.DEFAULT_SETTINGS.retry(),
                new StepTimeouts(Duration.ofSeconds(100), Duration.ofMinutes(10), Duration.ofHours(3)),
                ExecutorHarness.DEFAULT_SETTINGS.polls());
        ExecutorHarness local = new ExecutorHarness(ingest, platform, store, new FakeRunClock(), settings);
        ingest.start("ing0").pollForeverFor("ing0", running("ing0"));
        ingest.start("ing1").pollFor("ing1", running("ing1"), finished("ing1", "NO_CHANGES", false));
        PipelineRun queued = local.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1, G2)));

        local.executor.execute(queued.id());

        PipelineRun run = local.run(queued.id());
        List<RunUnit> units = local.units.findByRunId(queued.id());
        assertThat(units.get(0).status()).isEqualTo(UnitStatus.FAILED);
        assertThat(units.get(0).error().code()).isEqualTo("STEP_TIMEOUT");
        assertThat(units.get(1).status()).isEqualTo(UnitStatus.NO_CHANGES);
        assertThat(run.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(local.steps.findByRunId(queued.id())).extracting(PipelineStep::unitId)
                .containsExactly(units.get(0).id(), units.get(1).id());
    }

    @Test
    void stepAttemptsAreNumberedPerUnit() {
        ingest.start("a0").poll(failed("a0", "SOURCE_UNAVAILABLE", "down"))
                .start("a1").poll(finished("a1", "NO_CHANGES", false))
                .start("b0").poll(finished("b0", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1, G2))));

        List<RunUnit> units = units(run);
        assertThat(steps(units.get(0), StepKind.INGEST)).extracting(PipelineStep::attempt).containsExactly(1, 2);
        assertThat(steps(units.get(1), StepKind.INGEST)).extracting(PipelineStep::attempt).containsExactly(1);
        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
    }

    @Test
    void aRunAbortingFailureSkipsEveryPendingUnit() {
        ingest.startFails(Kind.CONFLICT, 409);

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(units(run)).extracting(RunUnit::status)
                .containsExactly(UnitStatus.FAILED, UnitStatus.SKIPPED, UnitStatus.SKIPPED);
        assertThat(units(run).get(0).error().code()).isEqualTo("INGEST_BUSY");
        assertThat(units(run).get(1).error().code()).isEqualTo("UNIT_SKIPPED");
        assertThat(units(run).get(1).error().message())
                .isEqualTo("skipped after INGEST_BUSY on unit " + units(run).get(0).label());
        assertThat(units(run).get(1).startedAt()).isNull();
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INGEST_BUSY");
        assertThat(run.error().message()).isEqualTo("3 of 3 units failed: INGEST_BUSY, UNIT_SKIPPED");
        assertThat(ingest.startRequests).hasSize(1);
        assertThat(h.observer.unitEvents).contains("unit:1:SKIPPED", "unit:2:SKIPPED");
    }

    @Test
    void aRunAbortingFailureAfterASuccessfulUnitLeavesThePartialRun() {
        scriptPackedUnit("ing0", UUID.randomUUID());
        ingest.startFails(Kind.CONFLICT, 409);

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(units(run)).extracting(RunUnit::status)
                .containsExactly(UnitStatus.SUCCEEDED, UnitStatus.FAILED, UnitStatus.SKIPPED);
        assertThat(run.status()).isEqualTo(RunStatus.PARTIAL);
    }

    @Test
    void aFailureThatDoesNotAbortLetsTheNextUnitRun() {
        ingest.startFails(Kind.REJECTED, 422);
        scriptPackedUnit("ing1", UUID.randomUUID());
        scriptPackedUnit("ing2", UUID.randomUUID());

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(units(run)).extracting(RunUnit::status)
                .containsExactly(UnitStatus.FAILED, UnitStatus.SUCCEEDED, UnitStatus.SUCCEEDED);
        assertThat(units(run).get(0).error().code()).isEqualTo("INGEST_REJECTED");
        assertThat(run.status()).isEqualTo(RunStatus.PARTIAL);
    }

    @Test
    void anUnexpectedErrorFailsTheActiveUnitSkipsThePendingOnesAndFailsTheRun() {
        ingest.startThrows(new IllegalStateException("boom"));

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(units(run)).extracting(RunUnit::status)
                .containsExactly(UnitStatus.FAILED, UnitStatus.SKIPPED, UnitStatus.SKIPPED);
        assertThat(units(run).get(0).error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(units(run).get(1).error().message())
                .isEqualTo("skipped after INTERNAL_ERROR on unit " + units(run).get(0).label());
    }

    @Test
    void anUnexpectedErrorInALaterUnitKeepsTheEarlierOnesAndSkipsTheRest() {
        scriptPackedUnit("ing0", UUID.randomUUID());
        ingest.startThrows(new IllegalStateException("late boom"));

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, THREE));

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(run.error().message()).isEqualTo("java.lang.IllegalStateException: late boom");
        assertThat(units(run)).extracting(RunUnit::status).containsExactly(
                UnitStatus.SUCCEEDED, UnitStatus.FAILED, UnitStatus.SKIPPED);
        assertThat(units(run).get(1).error().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void ingestProgressIsWrittenOnlyWhenItChanges() {
        IngestProgress p1 = new IngestProgress("download", 2, 10L, "acta-2.json");
        IngestProgress p2 = new IngestProgress("download", 5, 10L, "acta-5.json");
        ingest.start("ing0")
                .poll(runningWithProgress("ing0", p1))
                .poll(runningWithProgress("ing0", p1))
                .poll(running("ing0"))
                .poll(runningWithProgress("ing0", p2))
                .poll(runningWithProgress("ing0", p2))
                .poll(finished("ing0", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1))));

        List<UnitProgress> written = h.observer.units.stream()
                .map(RunUnit::progress)
                .filter(progress -> progress != null)
                .toList();
        assertThat(written).hasSize(2);
        assertThat(written.get(0).step()).isEqualTo(StepKind.INGEST);
        assertThat(written.get(0).stage()).isEqualTo("download");
        assertThat(written.get(0).itemsProcessed()).isEqualTo(2);
        assertThat(written.get(0).itemsTotal()).isEqualTo(10L);
        assertThat(written.get(0).currentItem()).isEqualTo("acta-2.json");
        assertThat(written.get(1).itemsProcessed()).isEqualTo(5);
        assertThat(units(run).get(0).progress()).isNull();
        assertThat(units(run).get(0).status()).isEqualTo(UnitStatus.NO_CHANGES);
    }

    @Test
    void anUnknownTotalAndBlankTextAreStoredAsAbsent() {
        ingest.start("ing0")
                .poll(runningWithProgress("ing0", new IngestProgress(" ", 3, null, " ")))
                .poll(finished("ing0", "NO_CHANGES", false));

        execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1))));

        assertThat(h.observer.units).filteredOn(unit -> unit.progress() != null).singleElement()
                .satisfies(unit -> {
                    assertThat(unit.progress().stage()).isNull();
                    assertThat(unit.progress().itemsTotal()).isNull();
                    assertThat(unit.progress().currentItem()).isNull();
                    assertThat(unit.progress().itemsProcessed()).isEqualTo(3);
                });
    }

    @Test
    void aLongCurrentItemIsTruncated() {
        ingest.start("ing0")
                .poll(runningWithProgress("ing0", new IngestProgress("parse", 1, 2L, "x".repeat(400))))
                .poll(finished("ing0", "NO_CHANGES", false));

        execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1))));

        assertThat(h.observer.units).filteredOn(unit -> unit.progress() != null).singleElement()
                .satisfies(unit -> assertThat(unit.progress().currentItem()).hasSize(256));
    }

    @Test
    void fetchAndImportReportTheirStepWithoutItemCounts() {
        scriptPackedUnit("ing0", UUID.randomUUID());

        execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(G1))));

        List<UnitProgress> written = h.observer.units.stream()
                .map(RunUnit::progress)
                .filter(progress -> progress != null)
                .toList();
        assertThat(written).extracting(UnitProgress::step)
                .containsExactly(StepKind.FETCH_PACKAGE, StepKind.IMPORT);
        assertThat(written).allSatisfy(progress -> {
            assertThat(progress.itemsTotal()).isNull();
            assertThat(progress.itemsProcessed()).isZero();
            assertThat(progress.stage()).isNull();
        });
    }

    @Test
    void aUnitRetryRunPlansItsSingleScopeAndRunsTheFullChain() {
        UUID job = UUID.randomUUID();
        scriptPackedUnit("retry0", job);
        PipelineRun original = h.queueRun(PipelineSource.BCNESA, THREE);
        h.runs.update(original.fail(new RunError("INGEST_FAILED", "boom"), h.clock.now()));
        PipelineRun queued = h.runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.BCNESA,
                "2025-2026", new RunScope(List.of(G2)), false, RunTrigger.UNIT_RETRY, "operator", original.id(),
                UUID.randomUUID(), h.clock.now()));

        PipelineRun run = execute(queued);

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(units(run)).singleElement().satisfies(unit -> {
            assertThat(unit.unitKey()).isEqualTo(UnitKey.of(G2));
            assertThat(unit.ingestRunId()).isEqualTo("retry0");
        });
        assertThat(ingest.startRequests).singleElement()
                .satisfies(request -> assertThat(request.scope().filters()).containsExactly(G2));
    }
}
