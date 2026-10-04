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
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.junit.jupiter.api.Test;

/** Each resume rule: the executor continues from stored run and step state instead of starting over. */
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

    private List<PipelineStep> steps(PipelineRun run, StepKind kind) {
        return h.steps.findByRunId(run.id()).stream().filter(s -> s.kind() == kind).toList();
    }

    private PipelineStep runningStep(PipelineRun run, StepKind kind, String ref) {
        return h.steps.save(PipelineStep.start(UUID.randomUUID(), run.id(), kind, 1, h.clock.now(), ref));
    }

    private PipelineRun runningIngest(String ingestRunId) {
        PipelineRun run = h.queueRun();
        return h.runs.update(run.startIngest(ingestRunId, h.clock.now()));
    }

    private PipelineRun packed(String ingestRunId) {
        PipelineRun run = runningIngest(ingestRunId);
        return h.runs.update(run.packed(h.clock.now()));
    }

    private RunArtifact storeZip(PipelineRun run) {
        String key = "rfetm/2025-2026/" + run.id() + "/ingest-" + run.ingestRunId() + ".zip";
        StoredArtifact stored = store.store(key, new java.io.ByteArrayInputStream(ZIP));
        return h.artifactRows.add(new RunArtifact(UUID.randomUUID(), run.id(), ArtifactKind.ZIP, key,
                stored.sha256(), stored.sizeBytes(), h.clock.now()));
    }

    @Test
    void queuedRunWithRunningIngestStepIsInterruptedThenStartedAgain() {
        PipelineRun run = h.queueRun();
        runningStep(run, StepKind.INGEST, null);
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
    void runningIngestWithARefKeepsPollingTheSameIngestRun() {
        PipelineRun run = runningIngest("ing1");
        runningStep(run, StepKind.INGEST, "ing1");
        ingest.poll(running("ing1")).poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.startRequests).isEmpty();
        assertThat(ingest.polledIds).containsExactly("ing1", "ing1");
        assertThat(steps(result, StepKind.INGEST)).singleElement()
                .satisfies(step -> assertThat(step.status()).isEqualTo(StepStatus.SUCCEEDED));
    }

    @Test
    void runningIngestWithoutARefIsInterruptedAndRestartedOnTheSameRun() {
        PipelineRun run = runningIngest("ing1");
        runningStep(run, StepKind.INGEST, null);
        ingest.start("ing2").poll(finished("ing2", "NO_CHANGES", false));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(result.ingestRunId()).isEqualTo("ing2");
        assertThat(steps(result, StepKind.INGEST).get(0).error().code()).isEqualTo("INTERRUPTED");
    }

    @Test
    void finishedIngestStepDerivesTheRunTransition() {
        PipelineRun run = runningIngest("ing1");
        PipelineStep step = runningStep(run, StepKind.INGEST, "ing1");
        h.steps.save(step.succeed(h.clock.now(), "NO_CHANGES"));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.polledIds).isEmpty();
    }

    @Test
    void succeededIngestStepWithAnotherOutcomeMovesOnToThePackage() {
        PipelineRun run = runningIngest("ing1");
        PipelineStep step = runningStep(run, StepKind.INGEST, "ing1");
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
    void failedRetryableIngestStepIsRetriedAndNonRetryableFailsTheRun() {
        PipelineRun retryable = runningIngest("ing1");
        PipelineStep step = runningStep(retryable, StepKind.INGEST, "ing1");
        h.steps.save(step.fail(h.clock.now(), "SOURCE_UNAVAILABLE", new RunError("SOURCE_UNAVAILABLE", "down"), true));
        ingest.start("ing2").poll(finished("ing2", "NO_CHANGES", false));
        assertThat(execute(retryable).status()).isEqualTo(RunStatus.NO_CHANGES);

        PipelineRun fatal = h.runs.update(h.queueRun().startIngest("ingX", h.clock.now()));
        PipelineStep fatalStep = runningStep(fatal, StepKind.INGEST, "ingX");
        h.steps.save(fatalStep.fail(h.clock.now(), "FAILED", new RunError("INGEST_FAILED", "boom"), false));

        PipelineRun result = execute(fatal);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.error().code()).isEqualTo("INGEST_FAILED");
    }

    @Test
    void packedRunWithoutArtifactInterruptsTheRunningFetchAndFetchesAgain() {
        PipelineRun run = packed("ing1");
        runningStep(run, StepKind.FETCH_PACKAGE, "ing1");
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
    void packedRunWithRunningImportStepAndRefBindsTheJobAndPollsIt() {
        PipelineRun run = packed("ing1");
        storeZip(run);
        runningStep(run, StepKind.IMPORT, jobId.toString());
        platform.poll(job(jobId, "SUCCEEDED", null, season("2025-2026", "SUCCEEDED", counters(2, 2))));

        PipelineRun result = execute(run);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.importJobId()).isEqualTo(jobId);
        assertThat(platform.submissions).isEmpty();
        assertThat(ingest.fetchedIds).isEmpty();
        assertThat(h.reports.findByRunId(run.id())).isPresent();
    }

    @Test
    void packedRunWithRunningImportStepWithoutRefIsInterruptedAndResubmitted() {
        PipelineRun run = packed("ing1");
        storeZip(run);
        runningStep(run, StepKind.IMPORT, null);
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
    void importingRunPollsItsBoundJob() {
        PipelineRun run = packed("ing1");
        storeZip(run);
        PipelineStep step = runningStep(run, StepKind.IMPORT, jobId.toString());
        PipelineRun importing = h.runs.update(run.startImport(jobId, h.clock.now()));
        platform.poll(job(jobId, "IMPORTING", null))
                .poll(job(jobId, "PARTIAL", null, season("2025-2026", "FAILED", null)));

        PipelineRun result = execute(importing);

        assertThat(result.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(platform.polledIds).containsExactly(jobId, jobId);
        assertThat(platform.submissions).isEmpty();
        assertThat(h.steps.findByRunId(run.id()).stream().filter(s -> s.id().equals(step.id())).findFirst()
                .orElseThrow().outcome()).isEqualTo("PARTIAL");
    }

    @Test
    void importingRunWhoseJobDisappearedFailsWithoutAnotherAttempt() {
        PipelineRun run = packed("ing1");
        storeZip(run);
        runningStep(run, StepKind.IMPORT, jobId.toString());
        PipelineRun importing = h.runs.update(run.startImport(jobId, h.clock.now()));
        platform.pollFails(Kind.NOT_FOUND, 404);

        PipelineRun result = execute(importing);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.error().code()).isEqualTo("IMPORT_JOB_LOST");
        assertThat(steps(result, StepKind.IMPORT)).hasSize(1);
    }

    @Test
    void importingRunWithASucceededStepFinishesFromTheStep() {
        PipelineRun run = packed("ing1");
        storeZip(run);
        PipelineStep step = runningStep(run, StepKind.IMPORT, jobId.toString());
        h.steps.save(step.succeed(h.clock.now(), "SUCCEEDED"));
        PipelineRun importing = h.runs.update(run.startImport(jobId, h.clock.now()));

        PipelineRun result = execute(importing);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(platform.polledIds).isEmpty();
    }
}
