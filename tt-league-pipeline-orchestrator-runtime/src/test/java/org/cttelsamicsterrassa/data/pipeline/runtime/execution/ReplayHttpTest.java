package org.cttelsamicsterrassa.data.pipeline.runtime.execution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ExecutorHarness;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.runtime.artifact.FileSystemArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpClientsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpImportGateway;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.IngestServiceJobRunner;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

/** A replay end to end over HTTP: the stored ZIP is re-submitted and ingest is never called again. */
class ReplayHttpTest {

    private static final String RUNS = "/api/v1/ingest/runs";
    private static final String JOBS = "/api/v1/administration/import/jobs";
    private static final byte[] ZIP = "PK-the-package-zip".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path artifacts;

    private final StubHttpServer ingest = new StubHttpServer();
    private final StubHttpServer platform = new StubHttpServer();
    private final UUID originalJob = UUID.randomUUID();
    private final UUID replayJob = UUID.randomUUID();

    @AfterEach
    void stop() {
        ingest.close();
        platform.close();
    }

    private ExecutorHarness harness() {
        IngestServiceJobRunner ingestGateway = new IngestServiceJobRunner(HttpClientsConfiguration.restClient(
                RestClient.builder(), ingest.baseUrl(), "ingest-key", Duration.ofSeconds(2), Duration.ofSeconds(5)));
        HttpImportGateway importGateway = new HttpImportGateway(HttpClientsConfiguration.restClient(
                RestClient.builder(), platform.baseUrl(), "platform-key", Duration.ofSeconds(2),
                Duration.ofSeconds(5)), new ObjectMapper());
        return new ExecutorHarness(ingestGateway, importGateway, new FileSystemArtifactStore(artifacts),
                new FakeRunClock(), ExecutorHarness.DEFAULT_SETTINGS);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String jobJson(UUID id, String status, String errorDetail) {
        return "{\"importJobId\":\"" + id + "\",\"status\":\"" + status + "\",\"errorDetail\":"
                + (errorDetail == null ? "null" : "\"" + errorDetail + "\"") + ",\"seasonResults\":["
                + "{\"season\":\"2025-2026\",\"status\":\"SUCCEEDED\",\"errorDetail\":null,\"result\":{"
                + "\"filesSeen\":4,\"itemsPersisted\":5,\"executionIssues\":[]}}]}";
    }

    /** Runs an original whose import ends with the given job status; returns it. */
    private PipelineRun original(ExecutorHarness h, String importStatus) throws Exception {
        ingest.on("POST", RUNS, Response.json(202, "{\"runId\":\"ing1\"}"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, "{\"runId\":\"ing1\",\"status\":\"SUCCEEDED\","
                + "\"outcome\":\"SUCCEEDED\",\"retryable\":false,\"package\":\"/data/ing1.zip\",\"error\":null}"));
        ingest.on("GET", RUNS + "/ing1/package", Response.bytes(200, ZIP,
                Map.of("Content-Type", "application/zip", "X-Content-SHA256", sha256(ZIP))));
        platform.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + originalJob
                + "\",\"status\":\"QUEUED\",\"created\":true}"));
        platform.onRepeating("GET", JOBS + "/" + originalJob, Response.json(200, jobJson(originalJob, importStatus,
                "FAILED".equals(importStatus) ? "boom" : null)));
        PipelineRun queued = h.queueRun();
        h.executor.execute(queued.id());
        return h.run(queued.id());
    }

    private PipelineRun queueReplay(ExecutorHarness h, PipelineRun original) {
        return h.runs.create(PipelineRun.queue(UUID.randomUUID(), original.source(), original.season(),
                original.scope(), original.force(), RunTrigger.RETRY, "operator", original.id(), h.clock.now()));
    }

    @Test
    void aReplayResubmitsTheSameZipWithoutCallingIngest() throws Exception {
        ExecutorHarness h = harness();
        PipelineRun original = original(h, "FAILED");
        assertThat(original.status()).isEqualTo(RunStatus.FAILED);
        platform.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + replayJob
                + "\",\"status\":\"QUEUED\",\"created\":true}"));
        platform.on("GET", JOBS + "/" + replayJob, Response.json(200, jobJson(replayJob, "SUCCEEDED", null)));
        PipelineRun replay = queueReplay(h, original);

        h.executor.execute(replay.id());

        PipelineRun result = h.run(replay.id());
        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(h.units.findByRunId(result.id())).singleElement()
                .satisfies(unit -> assertThat(unit.importJobId()).isEqualTo(replayJob));
        assertThat(ingest.requestsTo("POST", RUNS)).hasSize(1);
        assertThat(ingest.requestsTo("GET", RUNS + "/ing1/package")).hasSize(1);
        assertThat(platform.requestsTo("POST", JOBS)).hasSize(2);
        String second = new String(platform.requestsTo("POST", JOBS).get(1).body(), StandardCharsets.ISO_8859_1);
        assertThat(second).contains(new String(ZIP, StandardCharsets.ISO_8859_1)).contains(result.id().toString());
        assertThat(h.steps.findByRunId(result.id())).extracting(PipelineStep::kind)
                .containsExactly(StepKind.IMPORT);
        assertThat(h.steps.findByRunId(result.id()).get(0).importJobReused()).isFalse();
    }

    @Test
    void anExistingJobAnswerEndsTheReplayReusedWithTheOriginalsJobId() throws Exception {
        ExecutorHarness h = harness();
        PipelineRun original = original(h, "SUCCEEDED");
        assertThat(original.status()).isEqualTo(RunStatus.SUCCEEDED);
        platform.on("POST", JOBS, Response.json(200, "{\"importJobId\":\"" + originalJob
                + "\",\"status\":\"SUCCEEDED\",\"created\":false}"));
        PipelineRun replay = queueReplay(h, original);

        h.executor.execute(replay.id());

        PipelineRun result = h.run(replay.id());
        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(h.units.findByRunId(result.id())).singleElement()
                .satisfies(unit -> assertThat(unit.importJobId()).isEqualTo(originalJob));
        assertThat(h.steps.findByRunId(result.id())).singleElement().satisfies(step -> {
            assertThat(step.importJobReused()).isTrue();
            assertThat(step.externalRef()).isEqualTo(originalJob.toString());
        });
        assertThat(ingest.requestsTo("POST", RUNS)).hasSize(1);
        assertThat(h.reports.findByRunId(result.id())).hasSize(1);
    }
}
