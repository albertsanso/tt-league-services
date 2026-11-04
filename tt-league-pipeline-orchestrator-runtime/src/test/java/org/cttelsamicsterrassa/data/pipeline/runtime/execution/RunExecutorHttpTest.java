package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.execution.ExecutionSettings;
import org.cttelsamicsterrassa.data.pipeline.core.execution.StepTimeouts;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ExecutorHarness;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunUnit;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.run.UnitStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.runtime.artifact.FileSystemArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpClientsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpImportGateway;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.IngestServiceJobRunner;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

/** The acceptance paths end to end over HTTP: real gateways and file store against two stub servers. */
class RunExecutorHttpTest {

    private static final String INGEST_KEY = "ingest-key";
    private static final String PLATFORM_KEY = "platform-key";
    private static final String RUNS = "/api/v1/ingest/runs";
    private static final String JOBS = "/api/v1/administration/import/jobs";
    private static final byte[] ZIP = "PK-the-package-zip".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path artifacts;

    private final StubHttpServer ingest = new StubHttpServer();
    private final StubHttpServer platform = new StubHttpServer();
    private final UUID jobId = UUID.randomUUID();

    @AfterEach
    void stop() {
        ingest.close();
        platform.close();
    }

    private ExecutorHarness harness(Duration readTimeout, ExecutionSettings settings) {
        IngestServiceJobRunner ingestGateway = new IngestServiceJobRunner(HttpClientsConfiguration.restClient(
                RestClient.builder(), ingest.baseUrl(), INGEST_KEY, Duration.ofSeconds(2), readTimeout));
        HttpImportGateway importGateway = new HttpImportGateway(HttpClientsConfiguration.restClient(
                RestClient.builder(), platform.baseUrl(), PLATFORM_KEY, Duration.ofSeconds(2), readTimeout),
                new ObjectMapper());
        return new ExecutorHarness(ingestGateway, importGateway, new FileSystemArtifactStore(artifacts),
                new FakeRunClock(), settings);
    }

    private ExecutorHarness harness() {
        return harness(Duration.ofSeconds(5), ExecutorHarness.DEFAULT_SETTINGS);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String runState(String id, String status, String outcome, boolean withPackage) {
        return "{\"runId\":\"" + id + "\",\"status\":\"" + status + "\",\"outcome\":"
                + (outcome == null ? "null" : "\"" + outcome + "\"") + ",\"retryable\":false,\"package\":"
                + (withPackage ? "\"/data/" + id + ".zip\"" : "null") + ",\"error\":null}";
    }

    private static Response runId(String id) {
        return Response.json(202, "{\"runId\":\"" + id + "\"}");
    }

    private void scriptPackage(String id) throws Exception {
        ingest.on("GET", RUNS + "/" + id + "/package", Response.bytes(200, ZIP,
                Map.of("Content-Type", "application/zip", "X-Content-SHA256", sha256(ZIP))));
    }

    private String jobJson(String status, String errorDetail) {
        return "{\"importJobId\":\"" + jobId + "\",\"status\":\"" + status + "\",\"errorDetail\":"
                + (errorDetail == null ? "null" : "\"" + errorDetail + "\"") + ",\"seasonResults\":["
                + "{\"season\":\"2024-2025\",\"status\":\"SUCCEEDED\",\"errorDetail\":null,\"result\":{"
                + "\"filesSeen\":3,\"itemsPersisted\":2,\"executionIssues\":[]}},"
                + "{\"season\":\"2025-2026\",\"status\":\"SUCCEEDED\",\"errorDetail\":null,\"result\":{"
                + "\"filesSeen\":4,\"itemsPersisted\":5,\"executionIssues\":[\"odd acta\"]}}]}";
    }

    private void scriptSuccessfulImport() {
        platform.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + jobId
                + "\",\"status\":\"QUEUED\",\"created\":true}"));
        platform.on("GET", JOBS + "/" + jobId, Response.json(200, jobJson("IMPORTING", null)),
                Response.json(200, jobJson("SUCCEEDED", null)));
    }

    private List<PipelineStep> steps(ExecutorHarness h, PipelineRun run, StepKind kind) {
        return h.steps.findByRunId(run.id()).stream().filter(s -> s.kind() == kind).toList();
    }

    @Test
    void successStoresTheZipWithItsChecksumAndTheImportReport() throws Exception {
        ingest.on("POST", RUNS, runId("ing1"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "RUNNING", null, false)),
                Response.json(200, runState("ing1", "SUCCEEDED", "SUCCEEDED", true)));
        scriptPackage("ing1");
        scriptSuccessfulImport();
        ExecutorHarness h = harness();
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(ingest.requests.stream().filter(r -> r.method().equals("POST")).findFirst().orElseThrow()
                .bodyText()).contains("\"correlationId\":\"" + run.id() + "\"");
        RunUnit unit = h.units.findByRunId(run.id()).get(0);
        assertThat(unit.ingestRunId()).isEqualTo("ing1");
        assertThat(unit.importJobId()).isEqualTo(jobId);
        Path zip = artifacts.resolve("rfetm/2025-2026/" + run.id() + "/0-season/ingest-ing1.zip");
        assertThat(Files.readAllBytes(zip)).isEqualTo(ZIP);
        assertThat(h.artifactRows.findByRunId(run.id())).singleElement().satisfies(artifact -> {
            assertThat(artifact.sha256()).isEqualTo(sha256(ZIP));
            assertThat(artifact.sizeBytes()).isEqualTo(ZIP.length);
        });
        ImportReport report = h.reports.findByUnitId(unit.id()).orElseThrow();
        assertThat(report.filesSeen()).isEqualTo(7);
        assertThat(report.itemsPersisted()).isEqualTo(7);
        assertThat(report.issues()).containsExactly("2025-2026: odd acta");
        assertThat(report.rawReport()).isEqualTo(jobJson("SUCCEEDED", null));

        StubHttpServer.Recorded upload = platform.requestsTo("POST", JOBS).get(0);
        assertThat(upload.header("X-API-Key")).isEqualTo(PLATFORM_KEY);
        assertThat(new String(upload.body(), StandardCharsets.ISO_8859_1))
                .contains(new String(ZIP, StandardCharsets.ISO_8859_1)).contains(run.id().toString());
        assertThat(ingest.requests).allSatisfy(r -> assertThat(r.header("X-API-Key")).isEqualTo(INGEST_KEY));
        assertThat(ingest.requestsTo("POST", RUNS).get(0).bodyText()).contains("\"mode\":\"snapshot\"");
    }

    @Test
    void aScopedRunSendsOneSingleScopeRunPerUnitAndMapsTheReportedProgress() throws Exception {
        UUID jobOne = UUID.randomUUID();
        ingest.on("POST", RUNS, runId("ing1"), runId("ing2"));
        ingest.on("GET", RUNS + "/ing1",
                Response.json(200, "{\"runId\":\"ing1\",\"status\":\"RUNNING\",\"outcome\":null,"
                        + "\"retryable\":false,\"package\":null,\"error\":null,\"progress\":{\"stage\":\"DOWNLOAD\","
                        + "\"itemsProcessed\":2,\"itemsTotal\":5,\"currentItem\":\"league two\"}}"),
                Response.json(200, runState("ing1", "SUCCEEDED", "SUCCEEDED", true)));
        ingest.on("GET", RUNS + "/ing2", Response.json(200, runState("ing2", "SUCCEEDED", "NO_CHANGES", false)));
        scriptPackage("ing1");
        platform.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + jobOne
                + "\",\"status\":\"QUEUED\",\"created\":true}"));
        platform.on("GET", JOBS + "/" + jobOne, Response.json(200, jobJson("SUCCEEDED", null).replace(
                jobId.toString(), jobOne.toString())));
        ExecutorHarness h = harness();
        ScopeFilter g1 = new ScopeFilter("SENIOR", "G1", null, null, null, List.of(3));
        ScopeFilter g2 = new ScopeFilter("SENIOR", "G2", null, null, null, List.of(3, 4));
        PipelineRun queued = h.queueRun(PipelineSource.BCNESA, new RunScope(List.of(g1, g2)));

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        List<StubHttpServer.Recorded> starts = ingest.requestsTo("POST", RUNS);
        assertThat(starts).hasSize(2);
        assertThat(starts.get(0).bodyText()).contains("\"mode\":\"delta\"").contains("\"group\":\"G1\"")
                .doesNotContain("G2").contains("\"correlationId\":\"" + run.id() + "\"");
        assertThat(starts.get(1).bodyText()).contains("\"group\":\"G2\"").doesNotContain("G1");
        assertThat(h.units.findByRunId(run.id())).extracting(RunUnit::status)
                .containsExactly(UnitStatus.SUCCEEDED, UnitStatus.NO_CHANGES);
        assertThat(h.observer.units.stream().map(RunUnit::progress).filter(java.util.Objects::nonNull)
                .filter(p -> p.step() == StepKind.INGEST)).singleElement().satisfies(progress -> {
                    assertThat(progress.stage()).isEqualTo("DOWNLOAD");
                    assertThat(progress.itemsProcessed()).isEqualTo(2);
                    assertThat(progress.itemsTotal()).isEqualTo(5L);
                    assertThat(progress.currentItem()).isEqualTo("league two");
                });
        String key = "bcnesa/2025-2026/" + run.id() + "/0-" + h.units.findByRunId(run.id()).get(0).unitKey()
                .substring(0, 12) + "/ingest-ing1.zip";
        assertThat(Files.readAllBytes(artifacts.resolve(key))).isEqualTo(ZIP);
        assertThat(new String(platform.requestsTo("POST", JOBS).get(0).body(), StandardCharsets.ISO_8859_1))
                .contains(run.id() + "-0.zip");
    }

    @Test
    void noChangesNeverFetchesOrImports() {
        ingest.on("POST", RUNS, runId("ing1"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "SUCCEEDED", "NO_CHANGES", false)));
        ExecutorHarness h = harness();
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        assertThat(h.run(queued.id()).status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(ingest.requestsTo("GET", RUNS + "/ing1/package")).isEmpty();
        assertThat(platform.requests).isEmpty();
    }

    @Test
    void anIngestStart503IsRetriedWithBackoff() {
        ingest.on("POST", RUNS, Response.json(503, "{\"detail\":\"busy\"}"), runId("ing1"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "SUCCEEDED", "NO_CHANGES", false)));
        ExecutorHarness h = harness();
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(steps(h, run, StepKind.INGEST)).hasSize(2);
        assertThat(steps(h, run, StepKind.INGEST).get(0).error().code()).isEqualTo("INGEST_UNAVAILABLE");
        assertThat(h.fakeClock.sleeps).containsExactly(Duration.ofSeconds(30));
    }

    @Test
    void sourceUnavailableIsRetriedWithANewIngestRun() {
        ingest.on("POST", RUNS, runId("ing1"), runId("ing2"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "FAILED", "SOURCE_UNAVAILABLE", false)));
        ingest.on("GET", RUNS + "/ing2", Response.json(200, runState("ing2", "SUCCEEDED", "NO_CHANGES", false)));
        ExecutorHarness h = harness();
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(h.units.findByRunId(run.id()).get(0).ingestRunId()).isEqualTo("ing2");
        assertThat(steps(h, run, StepKind.INGEST).get(0).error().code()).isEqualTo("SOURCE_UNAVAILABLE");
    }

    @Test
    void anIngestRunStillRunningAtTheDeadlineTimesOut() {
        ExecutionSettings settings = new ExecutionSettings(ExecutorHarness.DEFAULT_SETTINGS.retry(),
                new StepTimeouts(Duration.ofSeconds(60), Duration.ofMinutes(10), Duration.ofHours(3)),
                ExecutorHarness.DEFAULT_SETTINGS.polls());
        ingest.on("POST", RUNS, runId("ing1"));
        ingest.onRepeating("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "RUNNING", null, false)));
        ExecutorHarness h = harness(Duration.ofSeconds(5), settings);
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("STEP_TIMEOUT");
        assertThat(steps(h, run, StepKind.INGEST)).hasSize(1);
    }

    @Test
    void aStubDelayLongerThanTheReadTimeoutCountsAsUnavailable() {
        ingest.on("POST", RUNS, runId("slow").after(Duration.ofMillis(1500)), runId("ing1"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "SUCCEEDED", "NO_CHANGES", false)));
        ExecutorHarness h = harness(Duration.ofMillis(300), ExecutorHarness.DEFAULT_SETTINGS);
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(steps(h, run, StepKind.INGEST).get(0).error().code()).isEqualTo("INGEST_UNAVAILABLE");
        assertThat(steps(h, run, StepKind.INGEST).get(0).retryable()).isTrue();
    }

    @Test
    void aFailedImportJobFailsTheRunAndKeepsTheReport() throws Exception {
        ingest.on("POST", RUNS, runId("ing1"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "SUCCEEDED", "SUCCEEDED", true)));
        scriptPackage("ing1");
        platform.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + jobId
                + "\",\"status\":\"QUEUED\",\"created\":true}"));
        platform.on("GET", JOBS + "/" + jobId, Response.json(200, jobJson("FAILED", "database unavailable")));
        ExecutorHarness h = harness();
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("IMPORT_FAILED");
        assertThat(run.error().message()).isEqualTo("database unavailable");
        assertThat(h.reports.findByRunId(run.id())).singleElement()
                .satisfies(report -> assertThat(report.importStatus()).isEqualTo("FAILED"));
    }

    @Test
    void aPublishedShrinkConflictFailsTheRunWithoutRetry() throws Exception {
        ingest.on("POST", RUNS, runId("ing1"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, runState("ing1", "SUCCEEDED", "SUCCEEDED", true)));
        scriptPackage("ing1");
        platform.on("POST", JOBS, Response.json(409, "{\"detail\":\"would shrink published actas\"}"));
        ExecutorHarness h = harness();
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.error().code()).isEqualTo("IMPORT_SHRINK");
        assertThat(run.error().message()).contains("would shrink published actas");
        assertThat(steps(h, run, StepKind.IMPORT)).hasSize(1);
        assertThat(platform.requestsTo("POST", JOBS)).hasSize(1);
    }

    @Test
    void aLostIngestRunIsFollowedByANewIngestRun() {
        ingest.on("POST", RUNS, runId("ing1"), runId("ing2"));
        ingest.on("GET", RUNS + "/ing1", Response.json(404, "{\"detail\":\"unknown run\"}"));
        ingest.on("GET", RUNS + "/ing2", Response.json(200, runState("ing2", "SUCCEEDED", "NO_CHANGES", false)));
        ExecutorHarness h = harness();
        PipelineRun queued = h.queueRun();

        h.executor.execute(queued.id());

        PipelineRun run = h.run(queued.id());
        assertThat(run.status()).isEqualTo(RunStatus.NO_CHANGES);
        assertThat(h.units.findByRunId(run.id()).get(0).ingestRunId()).isEqualTo("ing2");
        PipelineStep lost = steps(h, run, StepKind.INGEST).get(0);
        assertThat(lost.error().code()).isEqualTo("INGEST_RUN_LOST");
        assertThat(lost.retryable()).isTrue();
    }
}
