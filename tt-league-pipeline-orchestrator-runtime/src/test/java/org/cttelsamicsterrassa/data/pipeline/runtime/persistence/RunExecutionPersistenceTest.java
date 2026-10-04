package org.cttelsamicsterrassa.data.pipeline.runtime.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.ExecutorHarness;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.ImportReport;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineStep;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepKind;
import org.cttelsamicsterrassa.data.pipeline.core.run.StepStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.ImportReportRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineStepRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.RunArtifactRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.artifact.FileSystemArtifactStore;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpClientsConfiguration;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.HttpImportGateway;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.IngestServiceJobRunner;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

/** The executor on the JPA repositories against PostgreSQL; skipped without Docker. */
class RunExecutionPersistenceTest extends AbstractPersistenceTest {

    private static final byte[] ZIP = "PK-persisted-zip".getBytes(StandardCharsets.UTF_8);
    private static final String RUNS = "/api/v1/ingest/runs";
    private static final String JOBS = "/api/v1/administration/import/jobs";

    @Autowired
    PipelineRunRepository runs;
    @Autowired
    PipelineStepRepository steps;
    @Autowired
    RunArtifactRepository artifactRows;
    @Autowired
    ImportReportRepository reports;

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

    private RunExecutor executor() {
        IngestServiceJobRunner ingestGateway = new IngestServiceJobRunner(HttpClientsConfiguration.restClient(
                RestClient.builder(), ingest.baseUrl(), "k", Duration.ofSeconds(2), Duration.ofSeconds(5)));
        HttpImportGateway importGateway = new HttpImportGateway(HttpClientsConfiguration.restClient(
                RestClient.builder(), platform.baseUrl(), "k", Duration.ofSeconds(2), Duration.ofSeconds(5)),
                new ObjectMapper());
        return new RunExecutor(runs, steps, artifactRows, reports, ingestGateway, importGateway,
                new FileSystemArtifactStore(artifacts), new FakeRunClock(T0), RunObserver.none(),
                ExecutorHarness.DEFAULT_SETTINGS);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String job(String status) {
        return "{\"importJobId\":\"" + jobId + "\",\"status\":\"" + status + "\",\"seasonResults\":["
                + "{\"season\":\"2025-2026\",\"status\":\"SUCCEEDED\",\"result\":{\"filesSeen\":6,"
                + "\"itemsPersisted\":4}}]}";
    }

    @Test
    void successPathPersistsStepsArtifactAndReport() throws Exception {
        ingest.on("POST", RUNS, Response.json(202, "{\"runId\":\"ing1\"}"));
        ingest.on("GET", RUNS + "/ing1", Response.json(200, "{\"status\":\"SUCCEEDED\",\"outcome\":\"SUCCEEDED\","
                + "\"retryable\":false,\"package\":\"/data/ing1.zip\",\"error\":null}"));
        ingest.on("GET", RUNS + "/ing1/package", Response.bytes(200, ZIP,
                Map.of("X-Content-SHA256", sha256(ZIP))));
        platform.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + jobId
                + "\",\"status\":\"QUEUED\",\"created\":true}"));
        platform.on("GET", JOBS + "/" + jobId, Response.json(200, job("SUCCEEDED")));
        PipelineRun queued = runs.create(queued(org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource.RFETM));

        executor().execute(queued.id());

        PipelineRun run = runs.findById(queued.id()).orElseThrow();
        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(run.ingestRunId()).isEqualTo("ing1");
        assertThat(run.importJobId()).isEqualTo(jobId);
        assertThat(run.version()).isGreaterThanOrEqualTo(4);
        assertThat(steps.findByRunId(run.id())).extracting(s -> s.kind() + "/" + s.status() + "/" + s.externalRef())
                .containsExactly("INGEST/SUCCEEDED/ing1", "FETCH_PACKAGE/SUCCEEDED/ing1",
                        "IMPORT/SUCCEEDED/" + jobId);
        assertThat(artifactRows.findByRunId(run.id())).singleElement()
                .satisfies(a -> assertThat(a.sha256()).isEqualTo(sha256(ZIP)));
        ImportReport report = reports.findByRunId(run.id()).orElseThrow();
        assertThat(report.filesSeen()).isEqualTo(6);
        assertThat(report.itemsPersisted()).isEqualTo(4);
    }

    @Test
    void recoversAStoredImportingRunByPollingItsJob() {
        PipelineRun run = runs.create(queued(org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource.BCNESA));
        run = runs.update(run.startIngest("ing1", T0));
        run = runs.update(run.packed(T0));
        run = runs.update(run.startImport(jobId, T0));
        steps.save(PipelineStep.start(UUID.randomUUID(), run.id(), StepKind.IMPORT, 1, T0, jobId.toString()));
        platform.on("GET", JOBS + "/" + jobId, Response.json(200, job("SUCCEEDED")));

        executor().execute(run.id());

        PipelineRun result = runs.findById(run.id()).orElseThrow();
        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(steps.findByRunId(run.id())).singleElement()
                .satisfies(s -> assertThat(s.status()).isEqualTo(StepStatus.SUCCEEDED));
        assertThat(reports.findByRunId(run.id())).isPresent();
        assertThat(platform.requestsTo("POST", JOBS)).isEmpty();
    }
}
