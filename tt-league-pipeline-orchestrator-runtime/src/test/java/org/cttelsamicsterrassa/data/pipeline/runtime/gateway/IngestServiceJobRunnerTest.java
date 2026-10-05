package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.cttelsamicsterrassa.data.pipeline.core.execution.port.FetchedPackage;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestMode;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.StoredArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class IngestServiceJobRunnerTest {

    private static final String KEY = "ingest-secret-key";
    private static final String RUNS = "/api/v1/ingest/runs";

    private final StubHttpServer server = new StubHttpServer();
    private final IngestServiceJobRunner gateway = gatewayFor(server, Duration.ofSeconds(5));

    @AfterEach
    void stop() {
        server.close();
    }

    private static IngestServiceJobRunner gatewayFor(StubHttpServer server, Duration readTimeout) {
        return new IngestServiceJobRunner(HttpClientsConfiguration.restClient(
                RestClient.builder(), server.baseUrl(), KEY, Duration.ofSeconds(2), readTimeout));
    }

    private static IngestRunRequest fullSeason() {
        return new IngestRunRequest(PipelineSource.RFETM, "2025-2026", IngestMode.SNAPSHOT, RunScope.fullSeason(), false);
    }

    private static GatewayException failureOf(Runnable call) {
        try {
            call.run();
        } catch (GatewayException e) {
            return e;
        }
        throw new AssertionError("expected a GatewayException");
    }

    @Test
    void startsAFullSeasonSnapshotRun() {
        server.on("POST", RUNS, Response.json(202, "{\"runId\":\"0123456789abcdef0123456789abcdef\"}"));

        String id = gateway.startRun(fullSeason());

        assertThat(id).isEqualTo("0123456789abcdef0123456789abcdef");
        StubHttpServer.Recorded request = server.requests.get(0);
        assertThat(request.header("X-API-Key")).isEqualTo(KEY);
        assertThat(request.header("Content-Type")).startsWith("application/json");
        assertThat(request.bodyText()).isEqualTo("{\"source\":\"RFETM\",\"season\":\"2025-2026\","
                + "\"stages\":[\"download\",\"parse\",\"package\"],\"mode\":\"snapshot\",\"force\":false,"
                + "\"allowPublishedShrink\":false}");
    }

    @Test
    void sendsForceWhenTheRunIsForced() {
        server.on("POST", RUNS, Response.json(202, "{\"runId\":\"r1\"}"));

        gateway.startRun(new IngestRunRequest(PipelineSource.RFETM, "2025-2026", IngestMode.SNAPSHOT,
                RunScope.fullSeason(), true));

        assertThat(server.requests.get(0).bodyText()).contains("\"force\":true")
                .contains("\"allowPublishedShrink\":false");
    }

    @Test
    void startsAScopedDeltaRunWithScopesAndNoNullKeys() {
        server.on("POST", RUNS, Response.json(202, "{\"runId\":\"r1\"}"));
        RunScope scope = new RunScope(List.of(
                new ScopeFilter("CAT", null, "FASE1", null, null, List.of(3, 4)),
                new ScopeFilter(null, "G1", null, null, "F", List.of())));

        gateway.startRun(new IngestRunRequest(PipelineSource.BCNESA, "2025-2026", IngestMode.DELTA, scope, false));

        assertThat(server.requests.get(0).bodyText()).isEqualTo("{\"source\":\"BCNESA\",\"season\":\"2025-2026\","
                + "\"stages\":[\"download\",\"parse\",\"package\"],\"mode\":\"delta\",\"force\":false,"
                + "\"allowPublishedShrink\":false,\"scopes\":[{\"category\":\"CAT\",\"phase\":\"FASE1\","
                + "\"matchDays\":[3,4]},{\"group\":\"G1\",\"gender\":\"F\"}]}");
    }

    @Test
    void mapsStartStatusesToGatewayKinds() {
        Map<Integer, Kind> expected = Map.of(
                400, Kind.REJECTED, 409, Kind.CONFLICT, 422, Kind.REJECTED, 404, Kind.NOT_FOUND,
                500, Kind.UNAVAILABLE, 503, Kind.UNAVAILABLE);
        expected.forEach((status, kind) -> {
            server.on("POST", RUNS, Response.json(status, "{\"detail\":\"nope " + status + "\"}"));
            GatewayException e = failureOf(() -> gateway.startRun(fullSeason()));
            assertThat(e.kind()).as("status %s", status).isEqualTo(kind);
            assertThat(e.httpStatus()).isEqualTo(status);
            assertThat(e.getMessage()).contains("HTTP " + status).contains("nope " + status).doesNotContain(KEY);
        });
    }

    @Test
    void unauthorizedSaysToCheckTheKeyWithoutEchoingIt() {
        server.on("POST", RUNS, Response.json(401, "{\"detail\":\"invalid key\"}"));

        GatewayException e = failureOf(() -> gateway.startRun(fullSeason()));

        assertThat(e.kind()).isEqualTo(Kind.REJECTED);
        assertThat(e.getMessage()).contains("check the configured API key").doesNotContain(KEY);
    }

    @Test
    void anUnexpectedSuccessStatusOrRunIdIsAProtocolError() {
        server.on("POST", RUNS, Response.json(200, "{\"runId\":\"r1\"}"),
                Response.json(202, "{\"runId\":\"\"}"),
                Response.json(202, "{\"runId\":\"" + "x".repeat(65) + "\"}"),
                Response.json(202, "not json"));

        for (int i = 0; i < 4; i++) {
            assertThat(failureOf(() -> gateway.startRun(fullSeason())).kind()).isEqualTo(Kind.PROTOCOL);
        }
    }

    @Test
    void connectionRefusedIsUnavailable() {
        IngestServiceJobRunner dead;
        try (StubHttpServer stopped = new StubHttpServer()) {
            dead = gatewayFor(stopped, Duration.ofSeconds(1));
        }

        GatewayException e = failureOf(() -> dead.startRun(fullSeason()));

        assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(e.httpStatus()).isNull();
    }

    @Test
    void aReadTimeoutIsUnavailable() {
        server.on("GET", RUNS + "/slow", Response.json(200, "{}").after(Duration.ofMillis(1500)));
        IngestServiceJobRunner impatient = gatewayFor(server, Duration.ofMillis(300));

        GatewayException e = failureOf(() -> impatient.getRun("slow"));

        assertThat(e.kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void mapsARunningRun() {
        server.on("GET", RUNS + "/r1", Response.json(200, "{\"runId\":\"r1\",\"status\":\"RUNNING\","
                + "\"outcome\":null,\"retryable\":false,\"package\":null,\"error\":null,\"extra\":{\"a\":1}}"));

        IngestRunState state = gateway.getRun("r1");

        assertThat(state.status()).isEqualTo("RUNNING");
        assertThat(state.finished()).isFalse();
        assertThat(state.packageAvailable()).isFalse();
        assertThat(server.requests.get(0).header("X-API-Key")).isEqualTo(KEY);
    }

    @Test
    void mapsEveryOutcomeAndThePackagePresence() {
        server.on("GET", RUNS + "/r1",
                Response.json(200, state("SUCCEEDED", "SUCCEEDED", "\"/data/r1.zip\"", null)),
                Response.json(200, state("COMPLETED_WITH_ISSUES", "COMPLETED_WITH_ISSUES", "\"/data/r1.zip\"", null)),
                Response.json(200, state("SUCCEEDED", "NO_CHANGES", "null", null)),
                Response.json(200, state("FAILED", "SOURCE_UNAVAILABLE", "null", "\"source down\"")),
                Response.json(200, state("FAILED", "FAILED", "null", "\"parser exploded\"")));

        IngestRunState succeeded = gateway.getRun("r1");
        IngestRunState issues = gateway.getRun("r1");
        IngestRunState noChanges = gateway.getRun("r1");
        IngestRunState unavailable = gateway.getRun("r1");
        IngestRunState failed = gateway.getRun("r1");

        assertThat(succeeded.outcome()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.packageAvailable()).isTrue();
        assertThat(succeeded.finished()).isTrue();
        assertThat(issues.outcome()).isEqualTo("COMPLETED_WITH_ISSUES");
        assertThat(issues.packageAvailable()).isTrue();
        assertThat(noChanges.outcome()).isEqualTo("NO_CHANGES");
        assertThat(noChanges.packageAvailable()).isFalse();
        assertThat(unavailable.outcome()).isEqualTo("SOURCE_UNAVAILABLE");
        assertThat(unavailable.retryable()).isTrue();
        assertThat(unavailable.error()).isEqualTo("source down");
        assertThat(failed.outcome()).isEqualTo("FAILED");
        assertThat(failed.error()).isEqualTo("parser exploded");
    }

    private static String withStages(String stages) {
        return "{\"runId\":\"r1\",\"status\":\"SUCCEEDED\",\"outcome\":\"NO_CHANGES\",\"retryable\":false,"
                + "\"package\":null,\"error\":null,\"stages\":" + stages + "}";
    }

    @Test
    void sumsTheHealthCountersOverTheStages() {
        server.on("GET", RUNS + "/r1", Response.json(200, withStages("[{\"stage\":\"DOWNLOAD\",\"counters\":"
                + "{\"seen\":9,\"http_errors\":2,\"timeouts\":1,\"parse_errors\":0}},"
                + "{\"stage\":\"PARSE\",\"counters\":{\"parsed\":9,\"invalid\":3,\"http_errors\":0,"
                + "\"timeouts\":0,\"parse_errors\":4}},"
                + "{\"stage\":\"TEAMS\",\"counters\":{\"invalid\":1,\"http_errors\":1,\"timeouts\":0,"
                + "\"parse_errors\":2}},"
                + "{\"stage\":\"PACKAGE\",\"counters\":{\"invalid\":7,\"http_errors\":0,\"timeouts\":0,"
                + "\"parse_errors\":0}}]")));

        IngestRunState state = gateway.getRun("r1");

        // invalid counts as a parse error only on the parse and teams stages; the download http errors are summed
        assertThat(state.health()).isEqualTo(new IngestHealth(3, 1, 10));
    }

    @Test
    void healthIsUnknownWhenNoStageCarriesTheCounters() {
        server.on("GET", RUNS + "/r1", Response.json(200, withStages("[{\"stage\":\"DOWNLOAD\",\"counters\":"
                + "{\"seen\":9,\"invalid\":1}}]")), Response.json(200, withStages("[]")),
                Response.json(200, withStages("null")),
                Response.json(200, "{\"runId\":\"r1\",\"status\":\"RUNNING\"}"));

        assertThat(gateway.getRun("r1").health()).isNull();
        assertThat(gateway.getRun("r1").health()).isNull();
        assertThat(gateway.getRun("r1").health()).isNull();
        assertThat(gateway.getRun("r1").health()).isNull();
    }

    @Test
    void zeroHealthCountersAreKnownHealth() {
        server.on("GET", RUNS + "/r1", Response.json(200, withStages("[{\"stage\":\"DOWNLOAD\",\"counters\":"
                + "{\"http_errors\":0,\"timeouts\":0,\"parse_errors\":0}}]")));

        assertThat(gateway.getRun("r1").health()).isEqualTo(new IngestHealth(0, 0, 0));
    }

    @Test
    void aNegativeOrNonNumericHealthCounterIsAProtocolError() {
        server.on("GET", RUNS + "/r1",
                Response.json(200, withStages("[{\"stage\":\"DOWNLOAD\",\"counters\":{\"http_errors\":-1}}]")),
                Response.json(200, withStages("[{\"stage\":\"PARSE\",\"counters\":{\"parse_errors\":-3}}]")),
                Response.json(200, withStages("[{\"stage\":\"PARSE\",\"counters\":{\"timeouts\":\"many\"}}]")));

        assertThat(failureOf(() -> gateway.getRun("r1")).kind()).isEqualTo(Kind.PROTOCOL);
        assertThat(failureOf(() -> gateway.getRun("r1")).kind()).isEqualTo(Kind.PROTOCOL);
        assertThat(failureOf(() -> gateway.getRun("r1")).kind()).isEqualTo(Kind.PROTOCOL);
    }

    private static String state(String status, String outcome, String pkg, String error) {
        return "{\"runId\":\"r1\",\"status\":\"" + status + "\",\"outcome\":\"" + outcome
                + "\",\"retryable\":" + outcome.equals("SOURCE_UNAVAILABLE") + ",\"package\":" + pkg
                + ",\"error\":" + error + "}";
    }

    @Test
    void unknownRunIsNotFound() {
        server.on("GET", RUNS + "/gone", Response.json(404, "{\"detail\":\"unknown run\"}"));

        GatewayException e = failureOf(() -> gateway.getRun("gone"));

        assertThat(e.kind()).isEqualTo(Kind.NOT_FOUND);
        assertThat(e.getMessage()).contains("unknown run");
    }

    @Test
    void streamsThePackageIntoTheSink() throws Exception {
        byte[] zip = "PK-fake-zip-bytes".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(zip);
        server.on("GET", RUNS + "/r1/package", Response.bytes(200, zip,
                Map.of("Content-Type", "application/zip", "X-Content-SHA256", sha)));

        FetchedPackage fetched = gateway.fetchPackage("r1", body -> {
            try {
                byte[] bytes = body.readAllBytes();
                return new StoredArtifact("k", sha256(bytes), bytes.length);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(fetched.declaredSha256()).isEqualTo(sha);
        assertThat(fetched.stored().sha256()).isEqualTo(sha);
        assertThat(fetched.stored().sizeBytes()).isEqualTo(zip.length);
        assertThat(server.requests.get(0).header("X-API-Key")).isEqualTo(KEY);
    }

    @Test
    void aMissingOrMalformedChecksumHeaderIsAProtocolErrorAndTheSinkIsNotCalled() {
        byte[] zip = "zip".getBytes(StandardCharsets.UTF_8);
        server.on("GET", RUNS + "/r1/package",
                Response.bytes(200, zip, Map.of("Content-Type", "application/zip")),
                Response.bytes(200, zip, Map.of("X-Content-SHA256", "ABCDEF".repeat(10) + "ABCD")),
                Response.bytes(200, zip, Map.of("X-Content-SHA256", "abc")));
        AtomicBoolean sinkCalled = new AtomicBoolean();

        for (int i = 0; i < 3; i++) {
            GatewayException e = failureOf(() -> gateway.fetchPackage("r1", body -> {
                sinkCalled.set(true);
                return new StoredArtifact("k", "x", 0);
            }));
            assertThat(e.kind()).isEqualTo(Kind.PROTOCOL);
        }
        assertThat(sinkCalled).isFalse();
    }

    @Test
    void packageErrorsMapToKinds() {
        server.on("GET", RUNS + "/r1/package", Response.json(404, "{\"detail\":\"no longer retained\"}"),
                Response.json(409, "{\"detail\":\"the run is still active\"}"),
                Response.json(500, "{}"));

        assertThat(failureOf(() -> gateway.fetchPackage("r1", b -> null)).kind()).isEqualTo(Kind.NOT_FOUND);
        assertThat(failureOf(() -> gateway.fetchPackage("r1", b -> null)).kind()).isEqualTo(Kind.CONFLICT);
        assertThat(failureOf(() -> gateway.fetchPackage("r1", b -> null)).kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void sinkFailuresPassThroughUnchanged() {
        byte[] zip = "zip".getBytes(StandardCharsets.UTF_8);
        server.on("GET", RUNS + "/r1/package", Response.bytes(200, zip,
                Map.of("X-Content-SHA256", "0".repeat(64))));
        IllegalStateException boom = new IllegalStateException("disk full");

        assertThatThrownBy(() -> gateway.fetchPackage("r1", body -> {
            throw boom;
        })).isSameAs(boom);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
