package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.counters;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.created;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.job;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.season;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.finished;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.running;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.StoredArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ExecutorHarness;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.PackageResponse;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitKey;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitPlanner;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.junit.jupiter.api.Test;

/** Each resume rule: the executor continues from stored run, unit and step state instead of starting over. */
class RunExecutorResumeTest {

    private static final byte[] ZIP = "zip-content".getBytes(StandardCharsets.UTF_8);

    private final ScriptedIngestGateway ingest = new ScriptedIngestGateway();
    private final ScriptedImportGateway platform = new ScriptedImportGateway();
    private final InMemoryArtifactStore store = new InMemoryArtifactStore();
    private final ExecutorHarness h = new ExecutorHarness(ingest, platform, store);
    private final UUID jobId = UUID.randomUUID();

    private PipelineRun execute(PipelineRun run) {
        h.executor.execute(run.id());
        return h.run(run.id());
    }

    private RunUnit unit(PipelineRun run) {
        return h.units.findByRunId(run.id()).get(0);
    }

    private RunUnit unit(PipelineRun run, int ordinal) {
        return h.units.findByRunId(run.id()).get(ordinal);
    }

    private List<PipelineStep> steps(PipelineRun run, StepKind kind) {
        return h.steps.findByRunId(run.id()).stream().filter(s -> s.kind() == kind).toList();
    }

    private PipelineStep runningStep(RunUnit unit, StepKind kind, String ref) {
        return h.steps.save(
                PipelineStep.start(UUID.randomUUID(), unit.runId(), unit.id(), kind, 1, h.clock.now(), ref));
    }

    /** A run that was started by an earlier executor: units planned and the run RUNNING. */
    private PipelineRun startedRun(RunScope scope) {
        PipelineRun queued = h.queueRun(PipelineSource.RFETM, scope);
        h.units.addAll(UnitPlanner.plan(queued.id(), scope));
        return h.runs.update(queued.start(h.clock.now()));
    }

    private PipelineRun startedRun() {
        return startedRun(RunScope.fullSeason());
    }

    private RunUnit update(RunUnit unit) {
        return h.units.update(unit);
    }

    private RunUnit runningIngest(PipelineRun run, String ingestRunId) {
        return update(unit(run).startIngest(ingestRunId, h.clock.now()));
    }

    private RunUnit packed(PipelineRun run, String ingestRunId) {
        return update(runningIngest(run, ingestRunId).packed(h.clock.now()));
    }

    private RunArtifact storeZip(RunUnit unit) {
        String key = "rfetm/2025-2026/" + unit.runId() + "/0-season/ingest-" + unit.ingestRunId() + ".zip";
        StoredArtifact stored = store.store(key, new java.io.ByteArrayInputStream(ZIP));
        return h.artifactRows.add(new RunArtifact(UUID.randomUUID(), unit.runId(), unit.id(), ArtifactKind.ZIP, key,
                stored.sha256(), stored.sizeBytes(), h.clock.now()));
    }

    @Test
    void aQueuedRunWithPlannedUnitsIsStartedWithoutPlanningAgain() {
        PipelineRun queued = h.queueRun();
        h.units.addAll(UnitPlanner.plan(queued.id(), RunScope.fullSeason()));
        ingest.start("ing1").poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun result = execute(queued);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(h.units.findByRunId(queued.id())).hasSize(1);
    }

    @Test
    void aPendingUnitWithARunningIngestStepIsInterruptedThenStartedAgain() {
        PipelineRun run = startedRun();
        runningStep(unit(run), StepKind.INGEST, null);
        ingest.start("ing2").poll(finished("ing2", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        List<PipelineStep> attempts = steps(result, StepKind.INGEST);
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).error().code()).isEqualTo("INTERRUPTED");
        assertThat(attempts.get(0).retryable()).isTrue();
        assertThat(h.fakeClock.sleeps).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void aRunningIngestWithARefKeepsPollingTheSameIngestRun() {
        PipelineRun run = startedRun();
        runningIngest(run, "ing1");
        runningStep(unit(run), StepKind.INGEST, "ing1");
        ingest.poll(running("ing1")).poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.startRequests).isEmpty();
        assertThat(ingest.polledIds).containsExactly("ing1", "ing1");
        assertThat(steps(result, StepKind.INGEST)).singleElement()
                .satisfies(step -> assertThat(step.status()).isEqualTo(StepStatus.SUCCEEDED));
    }

    @Test
    void aRunningIngestWithoutARefIsInterruptedAndRestartedOnTheSameUnit() {
        PipelineRun run = startedRun();
        runningIngest(run, "ing1");
        runningStep(unit(run), StepKind.INGEST, null);
        ingest.start("ing2").poll(finished("ing2", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(unit(result).ingestRunId()).isEqualTo("ing2");
        assertThat(steps(result, StepKind.INGEST).get(0).error().code()).isEqualTo("INTERRUPTED");
    }

    @Test
    void aFinishedIngestStepDerivesTheUnitTransition() {
        PipelineRun run = startedRun();
        RunUnit unit = runningIngest(run, "ing1");
        PipelineStep step = runningStep(unit, StepKind.INGEST, "ing1");
        h.steps.save(step.succeed(h.clock.now(), "NO_CHANGES"));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(unit(result).status()).isEqualTo(UnitStatus.NO_CHANGES);
        assertThat(ingest.polledIds).isEmpty();
    }

    @Test
    void aSucceededIngestStepWithAnotherOutcomeMovesOnToThePackage() {
        PipelineRun run = startedRun();
        RunUnit unit = runningIngest(run, "ing1");
        PipelineStep step = runningStep(unit, StepKind.INGEST, "ing1");
        h.steps.save(step.succeed(h.clock.now(), "SUCCEEDED"));
        ingest.packageResponse(PackageResponse.valid(ZIP));
        platform.submit(created(jobId)).poll(job(jobId, "SUCCEEDED", null,
                season("2025-2026", "SUCCEEDED", counters(1, 1))));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(ingest.fetchedIds).containsExactly("ing1");
        assertThat(ingest.startRequests).isEmpty();
    }

    @Test
    void aFailedRetryableIngestStepIsRetriedAndANonRetryableFailsTheUnit() {
        PipelineRun retryable = startedRun();
        RunUnit retryUnit = runningIngest(retryable, "ing1");
        PipelineStep step = runningStep(retryUnit, StepKind.INGEST, "ing1");
        h.steps.save(step.fail(h.clock.now(), "SOURCE_UNAVAILABLE", new RunError("SOURCE_UNAVAILABLE", "down"), true));
        ingest.start("ing2").poll(finished("ing2", "NO_CHANGES", false));
        assertThat(execute(retryable).status()).isEqualTo(RunStatus.NO_CHANGES);

        PipelineRun fatal = h.runs.update(h.queueRun(PipelineSource.BCNESA, RunScope.fullSeason()).start(h.clock.now()));
        h.units.addAll(UnitPlanner.plan(fatal.id(), RunScope.fullSeason()));
        RunUnit fatalUnit = update(unit(fatal).startIngest("ingX", h.clock.now()));
        PipelineStep fatalStep = runningStep(fatalUnit, StepKind.INGEST, "ingX");
        h.steps.save(fatalStep.fail(h.clock.now(), "FAILED", new RunError("INGEST_FAILED", "boom"), false));

        PipelineRun result = execute(fatal);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.error().code()).isEqualTo("INGEST_FAILED");
        assertThat(unit(result).status()).isEqualTo(UnitStatus.FAILED);
    }

    @Test
    void aPackedUnitWithoutArtifactInterruptsTheRunningFetchAndFetchesAgain() {
        PipelineRun run = startedRun();
        RunUnit unit = packed(run, "ing1");
        runningStep(unit, StepKind.FETCH_PACKAGE, "ing1");
        ingest.packageResponse(PackageResponse.valid(ZIP));
        platform.submit(created(jobId)).poll(job(jobId, "SUCCEEDED", null,
                season("2025-2026", "SUCCEEDED", counters(1, 1))));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        List<PipelineStep> fetches = steps(result, StepKind.FETCH_PACKAGE);
        assertThat(fetches).hasSize(2);
        assertThat(fetches.get(0).error().code()).isEqualTo("INTERRUPTED");
        assertThat(fetches.get(1).status()).isEqualTo(StepStatus.SUCCEEDED);
    }

    @Test
    void aPackedUnitWithARunningImportStepAndRefBindsTheJobAndPollsIt() {
        PipelineRun run = startedRun();
        RunUnit unit = packed(run, "ing1");
        storeZip(unit);
        runningStep(unit, StepKind.IMPORT, jobId.toString());
        platform.poll(job(jobId, "SUCCEEDED", null, season("2025-2026", "SUCCEEDED", counters(2, 2))));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(unit(result).importJobId()).isEqualTo(jobId);
        assertThat(platform.submissions).isEmpty();
        assertThat(ingest.fetchedIds).isEmpty();
        assertThat(h.reports.findByUnitId(unit.id())).isPresent();
    }

    @Test
    void aPackedUnitWithARunningImportStepWithoutRefIsInterruptedAndResubmitted() {
        PipelineRun run = startedRun();
        RunUnit unit = packed(run, "ing1");
        storeZip(unit);
        runningStep(unit, StepKind.IMPORT, null);
        platform.submit(created(jobId)).poll(job(jobId, "SUCCEEDED", null,
                season("2025-2026", "SUCCEEDED", counters(1, 1))));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        List<PipelineStep> imports = steps(result, StepKind.IMPORT);
        assertThat(imports).hasSize(2);
        assertThat(imports.get(0).error().code()).isEqualTo("INTERRUPTED");
        assertThat(platform.submissions).hasSize(1);
    }

    @Test
    void anImportingUnitPollsItsBoundJob() {
        PipelineRun run = startedRun();
        RunUnit unit = packed(run, "ing1");
        storeZip(unit);
        PipelineStep step = runningStep(unit, StepKind.IMPORT, jobId.toString());
        update(unit.startImport(jobId, h.clock.now()));
        platform.poll(job(jobId, "IMPORTING", null))
                .poll(job(jobId, "PARTIAL", null, season("2025-2026", "FAILED", null)));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(unit(result).status()).isEqualTo(UnitStatus.PARTIAL);
        assertThat(platform.polledIds).containsExactly(jobId, jobId);
        assertThat(platform.submissions).isEmpty();
        assertThat(h.steps.findByRunId(run.id()).stream().filter(s -> s.id().equals(step.id())).findFirst()
                .orElseThrow().outcome()).isEqualTo("PARTIAL");
    }

    @Test
    void anImportingUnitWhoseJobDisappearedFailsWithoutAnotherAttempt() {
        PipelineRun run = startedRun();
        RunUnit unit = packed(run, "ing1");
        storeZip(unit);
        runningStep(unit, StepKind.IMPORT, jobId.toString());
        update(unit.startImport(jobId, h.clock.now()));
        platform.pollFails(Kind.NOT_FOUND, 404);

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.error().code()).isEqualTo("IMPORT_JOB_LOST");
        assertThat(steps(result, StepKind.IMPORT)).hasSize(1);
    }

    @Test
    void anImportingUnitWithASucceededStepFinishesFromTheStep() {
        PipelineRun run = startedRun();
        RunUnit unit = packed(run, "ing1");
        storeZip(unit);
        PipelineStep step = runningStep(unit, StepKind.IMPORT, jobId.toString());
        h.steps.save(step.succeed(h.clock.now(), "SUCCEEDED"));
        update(unit.startImport(jobId, h.clock.now()));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(platform.polledIds).isEmpty();
    }

    @Test
    void resumingBetweenUnitsSkipsTheFinishedOnesAndContinuesAtTheFirstActiveOne() {
        RunScope scope = new RunScope(List.of(
                new ScopeFilter("SENIOR", "G1", null, null, null, List.of(1)),
                new ScopeFilter("SENIOR", "G2", null, null, null, List.of(1)),
                new ScopeFilter("SENIOR", "G3", null, null, null, List.of(1))));
        PipelineRun run = startedRun(scope);
        update(unit(run, 0).startIngest("done", h.clock.now()).noChanges(h.clock.now()));
        ingest.start("ing2").poll(finished("ing2", "NO_CHANGES", false))
                .start("ing3").poll(finished("ing3", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.startRequests).hasSize(2);
        assertThat(h.units.findByRunId(run.id())).extracting(RunUnit::status)
                .containsOnly(UnitStatus.NO_CHANGES);
        assertThat(h.units.findByRunId(run.id())).extracting(RunUnit::ingestRunId)
                .containsExactly("done", "ing2", "ing3");
    }

    @Test
    void resumingMidUnitContinuesThatUnitBeforeTheNextOne() {
        RunScope scope = new RunScope(List.of(
                new ScopeFilter("SENIOR", "G1", null, null, null, List.of(1)),
                new ScopeFilter("SENIOR", "G2", null, null, null, List.of(1))));
        PipelineRun run = startedRun(scope);
        RunUnit first = update(unit(run, 0).startIngest("ing1", h.clock.now()));
        runningStep(first, StepKind.INGEST, "ing1");
        ingest.poll(finished("ing1", "NO_CHANGES", false))
                .start("ing2").poll(finished("ing2", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.polledIds).containsExactly("ing1", "ing2");
        assertThat(ingest.startRequests).hasSize(1);
    }

    @Test
    void aRunWithOnlyFinishedUnitsIsFinishedFromThem() {
        PipelineRun run = startedRun();
        update(unit(run).startIngest("ing1", h.clock.now()).noChanges(h.clock.now()));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.startRequests).isEmpty();
    }

    @Test
    void aBackfilledLegacyUnitIsResumedLikeAnyOther() {
        PipelineRun queued = h.queueRun(PipelineSource.RFETM,
                new RunScope(List.of(new ScopeFilter("SENIOR", "G1", null, null, null, List.of(1)),
                        new ScopeFilter("JUNIOR", null, null, null, null, List.of()))));
        h.units.addAll(List.of(RunUnit.plan(UUID.randomUUID(), queued.id(), 0, UnitKey.LEGACY, "Legacy scope",
                queued.scope())));
        PipelineRun run = h.runs.update(queued.start(h.clock.now()));
        ingest.start("ing1").poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(unit(result).unitKey()).isEqualTo(UnitKey.LEGACY);
        assertThat(ingest.startRequests).singleElement()
                .satisfies(request -> assertThat(request.scope().filters()).hasSize(2));
    }

    @Test
    void aRunningRunWithoutUnitsFailsWithAnInternalError() {
        PipelineRun queued = h.queueRun();
        PipelineRun run = h.runs.update(queued.start(h.clock.now()));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.error().code()).isEqualTo("INTERNAL_ERROR");
    }
}
