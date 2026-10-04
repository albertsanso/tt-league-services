package org.cttelsamicsterrassa.data.pipeline.core.execution;

import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.counters;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.created;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.job;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway.season;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.failed;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.finished;
import static org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.running;
import static org.assertj.core.api.Assertions.assertThat;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSubmission;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestMode;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ExecutorHarness;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.InMemoryArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ScriptedIngestGateway.PackageResponse;
import org.cttelsamicsterrassa.data.pipeline.core.run.ArtifactKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RunExecutorTest {

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

    private void scriptPackedIngest(String id) {
        ingest.start(id).poll(finished(id, "SUCCEEDED", true)).packageResponse(PackageResponse.valid(ZIP));
    }

    private void scriptImportSuccess() {
        platform.submit(created(jobId)).poll(job(jobId, "SUCCEEDED", null,
                season("2025-2026", "SUCCEEDED", counters(1, 1))));
    }

    @Test
    void fullSuccessWalksIngestFetchAndImport() {
        ingest.start("ing1").poll(running("ing1")).poll(finished("ing1", "SUCCEEDED", true))
                .packageResponse(PackageResponse.valid(ZIP));
        platform.submit(created(jobId)).poll(job(jobId, "IMPORTING", null)).poll(job(jobId, "SUCCEEDED", null,
                season("2024-2025", "SUCCEEDED", counters(3, 2), "late file"),
                season("2025-2026", "SUCCEEDED", counters(4, 5))));
        PipelineRun queued = h.queueRun();

        PipelineRun run = execute(queued);

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.ingestRunId()).isEqualTo("ing1");
        assertThat(run.importJobId()).isEqualTo(jobId);
        assertThat(h.steps.findByRunId(run.id())).extracting(s -> s.kind() + "/" + s.attempt() + "/" + s.status())
                .containsExactly("INGEST/1/SUCCEEDED", "FETCH_PACKAGE/1/SUCCEEDED", "IMPORT/1/SUCCEEDED");
        assertThat(steps(run, StepKind.INGEST).get(0).externalRef()).isEqualTo("ing1");
        assertThat(steps(run, StepKind.IMPORT).get(0).externalRef()).isEqualTo(jobId.toString());

        String key = "rfetm/2025-2026/" + run.id() + "/ingest-ing1.zip";
        assertThat(h.artifactRows.findByRunId(run.id())).singleElement().satisfies(artifact -> {
            assertThat(artifact.kind()).isEqualTo(ArtifactKind.ZIP);
            assertThat(artifact.storageKey()).isEqualTo(key);
            assertThat(artifact.sha256()).isEqualTo(InMemoryArtifactStore.sha256(ZIP));
            assertThat(artifact.sizeBytes()).isEqualTo(ZIP.length);
        });
        assertThat(platform.submissions).singleElement().satisfies(sent -> {
            assertThat(sent.fileName()).isEqualTo(run.id() + ".zip");
            assertThat(sent.bytes()).isEqualTo(ZIP);
            assertThat(sent.clientRunId()).isEqualTo(run.id());
        });
        ImportReport report = h.reports.findByRunId(run.id()).orElseThrow();
        assertThat(report.filesSeen()).isEqualTo(7);
        assertThat(report.itemsPersisted()).isEqualTo(7);
        assertThat(report.importStatus()).isEqualTo("SUCCEEDED");
        assertThat(report.issues()).containsExactly("2024-2025: late file");
        assertThat(h.fakeClock.sleeps).containsExactly(Duration.ofSeconds(15), Duration.ofSeconds(10));
    }

    @Test
    void fullSeasonRunsAsSnapshotAndScopedRunsAsDelta() {
        ingest.start("a").poll(finished("a", "NO_CHANGES", false));
        execute(h.queueRun());
        ScopeFilter filter = new ScopeFilter("CAT", null, null, null, null, List.of(3));
        ingest.start("b").poll(finished("b", "NO_CHANGES", false));
        execute(h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(filter))));

        List<IngestRunRequest> requests = ingest.startRequests;
        assertThat(requests.get(0).mode()).isEqualTo(IngestMode.SNAPSHOT);
        assertThat(requests.get(1).mode()).isEqualTo(IngestMode.DELTA);
        assertThat(requests.get(1).scope().filters()).containsExactly(filter);
    }

    @Test
    void forceIsPassedToTheIngestStart() {
        ingest.start("a").poll(finished("a", "NO_CHANGES", false));
        execute(h.queueRun(PipelineSource.RFETM, RunScope.fullSeason(), true));
        ingest.start("b").poll(finished("b", "NO_CHANGES", false));
        execute(h.queueRun(PipelineSource.BCNESA, RunScope.fullSeason(), false));

        assertThat(ingest.startRequests).extracting(IngestRunRequest::force).containsExactly(true, false);
    }

    @Test
    void noChangesEndsTheRunWithoutFetchOrImport() {
        ingest.start("ing1").poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.fetchedIds).isEmpty();
        assertThat(platform.submissions).isEmpty();
        assertThat(steps(run, StepKind.INGEST)).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(StepStatus.SUCCEEDED);
            assertThat(step.outcome()).isEqualTo("NO_CHANGES");
        });
        assertThat(h.observer.events).containsExactly("step:INGEST/1:RUNNING", "step:INGEST/1:RUNNING",
                "run:RUNNING_INGEST", "step:INGEST/1:SUCCEEDED", "run:NO_CHANGES");
    }

    @Test
    void completedWithIssuesStillPacksAndImports() {
        ingest.start("ing1").poll(finished("ing1", "COMPLETED_WITH_ISSUES", true))
                .packageResponse(PackageResponse.valid(ZIP));
        scriptImportSuccess();

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(steps(run, StepKind.INGEST).get(0).outcome()).isEqualTo("COMPLETED_WITH_ISSUES");
    }

    @Test
    void successWithoutPackageFailsWithIngestNoPackage() {
        ingest.start("ing1").poll(finished("ing1", "SUCCEEDED", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INGEST_NO_PACKAGE");
        assertThat(steps(run, StepKind.INGEST)).hasSize(1);
        assertThat(h.fakeClock.sleeps).isEmpty();
    }

    @Test
    void sourceUnavailableTwiceThenSuccessUsesThreeAttempts() {
        ingest.start("ing1").poll(failed("ing1", "SOURCE_UNAVAILABLE", "source down"))
                .start("ing2").poll(failed("ing2", "SOURCE_UNAVAILABLE", "source down"))
                .start("ing3").poll(finished("ing3", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(run.ingestRunId()).isEqualTo("ing3");
        List<PipelineStep> attempts = steps(run, StepKind.INGEST);
        assertThat(attempts).extracting(PipelineStep::attempt).containsExactly(1, 2, 3);
        assertThat(attempts.get(0).error().code()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(attempts.get(0).retryable()).isTrue();
        assertThat(h.fakeClock.sleeps).containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    @Test
    void sourceUnavailableFourTimesFailsAfterFourAttempts() {
        for (int i = 1; i <= 4; i++) {
            ingest.start("ing" + i).poll(failed("ing" + i, "SOURCE_UNAVAILABLE", "source down"));
        }

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(steps(run, StepKind.INGEST)).hasSize(4);
        assertThat(h.fakeClock.sleeps)
                .containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120));
    }

    @Test
    void ingestStartUnavailableThenSuccessRetries() {
        ingest.startFails(Kind.UNAVAILABLE, 503).start("ing1").poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        List<PipelineStep> attempts = steps(run, StepKind.INGEST);
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).error().code()).isEqualTo("INGEST_UNAVAILABLE");
        assertThat(attempts.get(0).externalRef()).isNull();
        assertThat(h.fakeClock.sleeps).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void ingestConflictIsBusyAndNotRetried() {
        ingest.startFails(Kind.CONFLICT, 409);

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INGEST_BUSY");
        assertThat(steps(run, StepKind.INGEST)).hasSize(1);
        assertThat(h.fakeClock.sleeps).isEmpty();
    }

    @Test
    void ingestRejectionsAndProtocolErrorsAreFinal() {
        ingest.startFails(Kind.REJECTED, 422);
        assertThat(execute(h.queueRun()).error().code()).isEqualTo("INGEST_REJECTED");

        ingest.startFails(Kind.PROTOCOL, 200);
        assertThat(execute(h.queueRun(PipelineSource.FCTT, RunScope.fullSeason())).error().code())
                .isEqualTo("PROTOCOL_ERROR");
    }

    @Test
    void ingestFailedIsFinalAndCarriesIngestError() {
        ingest.start("ing1").poll(failed("ing1", "FAILED", "parser exploded"));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INGEST_FAILED");
        assertThat(run.error().message()).isEqualTo("parser exploded");
        assertThat(steps(run, StepKind.INGEST)).hasSize(1);
        assertThat(h.fakeClock.sleeps).isEmpty();
    }

    @Test
    void unknownIngestOutcomeIsAProtocolError() {
        ingest.start("ing1").poll(finished("ing1", "SOMETHING_NEW", true));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.error().code()).isEqualTo("PROTOCOL_ERROR");
    }

    @Test
    void lostIngestRunIsRetriedWithANewIngestRun() {
        ingest.start("ing1").pollFails(Kind.NOT_FOUND, 404)
                .start("ing2").poll(finished("ing2", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(run.ingestRunId()).isEqualTo("ing2");
        PipelineStep first = steps(run, StepKind.INGEST).get(0);
        assertThat(first.error().code()).isEqualTo("INGEST_RUN_LOST");
        assertThat(first.retryable()).isTrue();
    }

    @Test
    void threeFailedPollsThenSuccessStayInOneAttempt() {
        ingest.start("ing1").pollFails(Kind.UNAVAILABLE, 503).pollFails(Kind.UNAVAILABLE, 503)
                .pollFails(Kind.UNAVAILABLE, 502).poll(finished("ing1", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(steps(run, StepKind.INGEST)).hasSize(1);
        assertThat(h.fakeClock.sleeps)
                .containsExactly(Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(120));
    }

    @Test
    void fourConsecutiveFailedPollsFailTheAttemptAsRetryable() {
        ingest.start("ing1");
        for (int i = 0; i < 4; i++) {
            ingest.pollFails(Kind.UNAVAILABLE, 503);
        }
        ingest.start("ing2").poll(finished("ing2", "NO_CHANGES", false));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        List<PipelineStep> attempts = steps(run, StepKind.INGEST);
        assertThat(attempts).hasSize(2);
        assertThat(attempts.get(0).error().code()).isEqualTo("INGEST_UNAVAILABLE");
        assertThat(attempts.get(0).retryable()).isTrue();
    }

    @Test
    void ingestStillRunningPastItsTimeoutFailsAndClipsTheLastSleep() {
        ExecutionSettings settings = new ExecutionSettings(ExecutorHarness.DEFAULT_SETTINGS.retry(),
                new StepTimeouts(Duration.ofSeconds(100), Duration.ofMinutes(10), Duration.ofHours(3)),
                ExecutorHarness.DEFAULT_SETTINGS.polls());
        ExecutorHarness local = new ExecutorHarness(ingest, platform, store, new FakeRunClock(), settings);
        ingest.start("ing1").pollForever(running("ing1"));
        PipelineRun queued = local.queueRun();

        local.executor.execute(queued.id());

        PipelineRun run = local.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("STEP_TIMEOUT");
        List<Duration> sleeps = local.fakeClock.sleeps;
        assertThat(sleeps.get(sleeps.size() - 1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(sleeps.stream().reduce(Duration.ZERO, Duration::plus)).isEqualTo(Duration.ofSeconds(100));
        assertThat(local.steps.findByRunId(queued.id())).hasSize(1);
    }

    @Test
    void aBackoffThatWouldCrossTheDeadlineIsNotTaken() {
        ExecutionSettings settings = new ExecutionSettings(ExecutorHarness.DEFAULT_SETTINGS.retry(),
                new StepTimeouts(Duration.ofSeconds(40), Duration.ofMinutes(10), Duration.ofHours(3)),
                ExecutorHarness.DEFAULT_SETTINGS.polls());
        ExecutorHarness local = new ExecutorHarness(ingest, platform, store, new FakeRunClock(), settings);
        ingest.start("ing1").poll(failed("ing1", "SOURCE_UNAVAILABLE", "down"))
                .start("ing2").poll(failed("ing2", "SOURCE_UNAVAILABLE", "down"));
        PipelineRun queued = local.queueRun();

        local.executor.execute(queued.id());

        PipelineRun run = local.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(local.steps.findByRunId(queued.id())).hasSize(2);
        assertThat(local.fakeClock.sleeps).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void packageUnavailableThenSuccessRetriesTheFetch() {
        ingest.start("ing1").poll(finished("ing1", "SUCCEEDED", true))
                .packageFails(Kind.UNAVAILABLE, 503).packageResponse(PackageResponse.valid(ZIP));
        scriptImportSuccess();

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        List<PipelineStep> fetches = steps(run, StepKind.FETCH_PACKAGE);
        assertThat(fetches).hasSize(2);
        assertThat(fetches.get(0).error().code()).isEqualTo("PACKAGE_UNAVAILABLE");
        assertThat(h.artifactRows.findByRunId(run.id())).hasSize(1);
    }

    @Test
    void packageNotFoundIsGoneAndFinal() {
        ingest.start("ing1").poll(finished("ing1", "SUCCEEDED", true)).packageFails(Kind.NOT_FOUND, 404);

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("PACKAGE_GONE");
        assertThat(steps(run, StepKind.FETCH_PACKAGE)).hasSize(1);
        assertThat(platform.submissions).isEmpty();
    }

    @Test
    void checksumMismatchFailsAndDeletesTheFile() {
        ingest.start("ing1").poll(finished("ing1", "SUCCEEDED", true))
                .packageResponse(new PackageResponse("0".repeat(64), ZIP));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("PACKAGE_CHECKSUM_MISMATCH");
        assertThat(store.exists("rfetm/2025-2026/" + run.id() + "/ingest-ing1.zip")).isFalse();
        assertThat(h.artifactRows.findByRunId(run.id())).isEmpty();
        assertThat(steps(run, StepKind.FETCH_PACKAGE)).hasSize(1);
    }

    @Test
    void importUnavailableThenDeduplicatedJobSucceeds() {
        scriptPackedIngest("ing1");
        platform.submitFails(Kind.UNAVAILABLE, 503).submit(new ImportSubmission(jobId, "SUCCEEDED", false))
                .poll(job(jobId, "SUCCEEDED", null, season("2025-2026", "SUCCEEDED", counters(1, 1))));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        List<PipelineStep> imports = steps(run, StepKind.IMPORT);
        assertThat(imports).hasSize(2);
        assertThat(imports.get(0).error().code()).isEqualTo("PLATFORM_UNAVAILABLE");
        assertThat(imports.get(1).externalRef()).isEqualTo(jobId.toString());
        assertThat(platform.submissions).hasSize(2);
    }

    @Test
    void importRejectionAndShrinkAreFinal() {
        scriptPackedIngest("ing1");
        platform.submitFails(Kind.REJECTED, 400);
        PipelineRun rejected = execute(h.queueRun());
        assertThat(rejected.error().code()).isEqualTo("IMPORT_REJECTED");
        assertThat(steps(rejected, StepKind.IMPORT)).hasSize(1);

        scriptPackedIngest("ing2");
        platform.submitFails(Kind.CONFLICT, 409);
        PipelineRun shrink = execute(h.queueRun(PipelineSource.BCNESA, RunScope.fullSeason()));
        assertThat(shrink.error().code()).isEqualTo("IMPORT_SHRINK");
        assertThat(steps(shrink, StepKind.IMPORT)).hasSize(1);
    }

    @Test
    void partialJobGivesAPartialRun() {
        scriptPackedIngest("ing1");
        platform.submit(created(jobId)).poll(job(jobId, "PARTIAL", null,
                season("2025-2026", "FAILED", null), season("2024-2025", "SUCCEEDED", counters(2, 2))));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.PARTIAL);
        assertThat(steps(run, StepKind.IMPORT).get(0).outcome()).isEqualTo("PARTIAL");
        assertThat(h.reports.findByRunId(run.id()).orElseThrow().importStatus()).isEqualTo("PARTIAL");
    }

    @Test
    void failedJobFailsTheRunAndKeepsTheReport() {
        scriptPackedIngest("ing1");
        platform.submit(created(jobId)).poll(job(jobId, "FAILED", "platform exploded",
                season("2025-2026", "FAILED", null)));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("IMPORT_FAILED");
        assertThat(run.error().message()).isEqualTo("platform exploded");
        assertThat(run.importJobId()).isEqualTo(jobId);
        ImportReport report = h.reports.findByRunId(run.id()).orElseThrow();
        assertThat(report.importStatus()).isEqualTo("FAILED");
        assertThat(report.issues()).containsExactly("platform exploded");
        assertThat(steps(run, StepKind.IMPORT)).hasSize(1);
    }

    @Test
    void unknownImportJobFailsWithImportJobLost() {
        scriptPackedIngest("ing1");
        platform.submit(created(jobId)).pollFails(Kind.NOT_FOUND, 404);

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("IMPORT_JOB_LOST");
        assertThat(steps(run, StepKind.IMPORT)).hasSize(1);
    }

    @Test
    void importPollsThatKeepFailingFailWithoutANewAttempt() {
        scriptPackedIngest("ing1");
        platform.submit(created(jobId));
        for (int i = 0; i < 4; i++) {
            platform.pollFails(Kind.UNAVAILABLE, 503);
        }

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("PLATFORM_UNAVAILABLE");
        assertThat(steps(run, StepKind.IMPORT)).hasSize(1);
        assertThat(platform.submissions).hasSize(1);
    }

    @Test
    void importStillRunningPastItsTimeoutFails() {
        ExecutionSettings settings = new ExecutionSettings(ExecutorHarness.DEFAULT_SETTINGS.retry(),
                new StepTimeouts(Duration.ofHours(3), Duration.ofMinutes(10), Duration.ofSeconds(100)),
                ExecutorHarness.DEFAULT_SETTINGS.polls());
        ExecutorHarness local = new ExecutorHarness(ingest, platform, store, new FakeRunClock(), settings);
        scriptPackedIngest("ing1");
        platform.submit(created(jobId)).pollForever(job(jobId, "IMPORTING", null));
        PipelineRun queued = local.queueRun();

        local.executor.execute(queued.id());

        PipelineRun run = local.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("STEP_TIMEOUT");
        assertThat(local.steps.findByRunId(queued.id()).stream().filter(s -> s.kind() == StepKind.IMPORT))
                .hasSize(1);
    }

    @Test
    void unexpectedGatewayExceptionFailsTheRunWithInternalError() {
        ingest.startThrows(new IllegalStateException("boom"));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(run.error().message()).isEqualTo("java.lang.IllegalStateException: boom");
        assertThat(steps(run, StepKind.INGEST)).singleElement().satisfies(step -> {
            assertThat(step.status()).isEqualTo(StepStatus.FAILED);
            assertThat(step.error().code()).isEqualTo("INTERNAL_ERROR");
        });
    }

    @Test
    void internalErrorMessageIsTruncated() {
        ingest.startThrows(new IllegalStateException("x".repeat(2000)));

        PipelineRun run = execute(h.queueRun());

        assertThat(run.error().message()).hasSize(500);
    }

    @Test
    void staleRunStopsWithoutFurtherWrites() {
        AtomicReference<Runnable> onStart = new AtomicReference<>(() -> { });
        ScriptedIngestGateway racing = new ScriptedIngestGateway() {
            @Override
            public String startRun(IngestRunRequest request) {
                onStart.get().run();
                return "ing1";
            }
        };
        ExecutorHarness local = new ExecutorHarness(racing, platform, store);
        PipelineRun queued = local.queueRun();
        onStart.set(() -> local.runs.update(local.run(queued.id())));

        local.executor.execute(queued.id());

        PipelineRun run = local.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.QUEUED);
        assertThat(run.version()).isEqualTo(1);
        assertThat(local.observer.events).containsExactly("step:INGEST/1:RUNNING", "step:INGEST/1:RUNNING");
    }

    @Test
    void interruptionLeavesTheRunActiveAndRestoresTheFlag() {
        ingest.start("ing1").poll(running("ing1"));
        h.fakeClock.interruptOnSleep();
        PipelineRun queued = h.queueRun();

        try {
            PipelineRun run = execute(queued);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(run.status()).isEqualTo(RunStatus.RUNNING_INGEST);
            assertThat(steps(run, StepKind.INGEST)).singleElement()
                    .satisfies(step -> assertThat(step.status()).isEqualTo(StepStatus.RUNNING));
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void terminalRunsAreLeftAlone() {
        ingest.start("ing1").poll(finished("ing1", "NO_CHANGES", false));
        PipelineRun done = execute(h.queueRun());
        int events = h.observer.events.size();

        h.executor.execute(done.id());

        assertThat(h.observer.events).hasSize(events);
        assertThat(ingest.startRequests).hasSize(1);
    }
}
