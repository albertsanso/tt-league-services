package org.cttelsamicsterrassa.data.pipeline.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.RunExecutor;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunStatus;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.DailyStatsAggregator;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import org.cttelsamicsterrassa.data.pipeline.runtime.persistence.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FEAT-00113 end to end: a run goes through the real gateways against an ingest and a platform stub, and the health of
 * the ingest step, the amended count of the import report, the statistics endpoints and the daily snapshot all agree.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "tt.pipeline.platform.api-key=test-platform-key",
            "tt.pipeline.platform.connect-timeout=PT2S",
            "tt.pipeline.platform.read-timeout=PT5S",
            "tt.pipeline.platform.poll-interval=PT1S",
            "tt.pipeline.ingest.api-key=test-ingest-key",
            "tt.pipeline.ingest.connect-timeout=PT2S",
            "tt.pipeline.ingest.read-timeout=PT5S",
            "tt.pipeline.ingest.poll-interval=PT1S",
            "tt.pipeline.artifacts.dir=${java.io.tmpdir}",
            "tt.pipeline.execution.recover-on-startup=false",
            "tt.pipeline.execution.max-retries=3",
            "tt.pipeline.execution.initial-backoff=PT30S",
            "tt.pipeline.execution.backoff-multiplier=2",
            "tt.pipeline.execution.max-backoff=PT5M",
            "tt.pipeline.execution.timeouts.ingest=PT3H",
            "tt.pipeline.execution.timeouts.fetch-package=PT10M",
            "tt.pipeline.execution.timeouts.import-job=PT3H",
            "tt.pipeline.execution.max-concurrent-runs=1",
            "spring.datasource.url=jdbc:postgresql://service-connection/unused",
            "spring.datasource.username=unused",
            "spring.datasource.password=unused",
            "tt.pipeline.statistics.zone=Europe/Madrid",
            "tt.pipeline.security.jwt-secret=0123456789abcdef0123456789abcdef"
        })
@Import({PostgresTestConfiguration.class, StatisticsIntegrationTest.FixedClock.class})
@Testcontainers(disabledWithoutDocker = true)
class StatisticsIntegrationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String RUNS = "/api/v1/ingest/runs";
    private static final String JOBS = "/api/v1/administration/import/jobs";
    private static final byte[] ZIP = "PK-the-package-zip".getBytes(StandardCharsets.UTF_8);
    // 2026-10-04 12:00 in Madrid
    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-10-04");

    private static final StubHttpServer INGEST = new StubHttpServer();
    private static final StubHttpServer PLATFORM = new StubHttpServer();
    private static final FakeRunClock CLOCK = new FakeRunClock(NOW);

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        FakeRunClock fixedClock() {
            return CLOCK;
        }
    }

    @DynamicPropertySource
    static void stubs(DynamicPropertyRegistry registry) {
        registry.add("tt.pipeline.ingest.base-url", () -> INGEST.baseUrl().toString());
        registry.add("tt.pipeline.platform.base-url", () -> PLATFORM.baseUrl().toString());
    }

    @AfterAll
    static void stopStubs() {
        INGEST.close();
        PLATFORM.close();
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PipelineRunRepository runs;
    @Autowired
    RunExecutor executor;
    @Autowired
    DailyStatsAggregator aggregator;

    @BeforeEach
    void clean() {
        CLOCK.advance(Duration.between(CLOCK.now(), NOW));
        jdbc.execute("TRUNCATE pipeline.daily_stats, pipeline.match_day, pipeline.pending_trigger, "
                + "pipeline.import_report, pipeline.run_artifact, pipeline.pipeline_step, pipeline.pipeline_run CASCADE");
        INGEST.requests.clear();
        PLATFORM.requests.clear();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String token() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject("alice").claim("permissions", List.of())
                .expirationTime(Date.from(Instant.now().plusSeconds(600))).build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private JsonNode get(String path) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token());
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new ObjectMapper().readTree(response.getBody());
    }

    private void scriptSuccessfulRun(UUID jobId) throws Exception {
        INGEST.on("POST", RUNS, Response.json(202, "{\"runId\":\"ing1\"}"));
        INGEST.on("GET", RUNS + "/ing1", Response.json(200, "{\"runId\":\"ing1\",\"status\":\"SUCCEEDED\","
                + "\"outcome\":\"SUCCEEDED\",\"retryable\":false,\"package\":\"/data/ing1.zip\",\"error\":null,"
                + "\"stages\":[{\"stage\":\"DOWNLOAD\",\"counters\":{\"seen\":9,\"http_errors\":2,\"timeouts\":1,"
                + "\"parse_errors\":0}},{\"stage\":\"PARSE\",\"counters\":{\"parsed\":9,\"invalid\":1,"
                + "\"http_errors\":0,\"timeouts\":0,\"parse_errors\":3}}]}"));
        INGEST.on("GET", RUNS + "/ing1/package", Response.bytes(200, ZIP,
                Map.of("Content-Type", "application/zip", "X-Content-SHA256", sha256(ZIP))));
        PLATFORM.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + jobId
                + "\",\"status\":\"QUEUED\",\"created\":true}"));
        PLATFORM.on("GET", JOBS + "/" + jobId, Response.json(200, "{\"importJobId\":\"" + jobId
                + "\",\"status\":\"SUCCEEDED\",\"errorDetail\":null,\"seasonResults\":[{\"season\":\"2025-2026\","
                + "\"status\":\"SUCCEEDED\",\"errorDetail\":null,\"result\":{\"filesSeen\":4,\"itemsPersisted\":5,"
                + "\"amendedPlayed\":2,\"executionIssues\":[]}}]}"));
    }

    @Test
    void aRunStoresTheIngestHealthAndTheAmendedCountAndTheStatisticsReadThemBack() throws Exception {
        scriptSuccessfulRun(UUID.randomUUID());
        PipelineRun queued = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "alice", null, CLOCK.now()));

        executor.execute(queued.id());

        assertThat(runs.findById(queued.id()).orElseThrow().status()).isEqualTo(RunStatus.SUCCEEDED);
        Map<String, Object> ingestStep = jdbc.queryForMap("SELECT http_errors, timeouts, parse_errors "
                + "FROM pipeline.pipeline_step WHERE kind = 'INGEST'");
        assertThat(ingestStep).containsEntry("http_errors", 2L).containsEntry("timeouts", 1L)
                .containsEntry("parse_errors", 4L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.pipeline_step WHERE kind <> 'INGEST' "
                + "AND http_errors IS NOT NULL", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT amended_played FROM pipeline.import_report", Long.class))
                .isEqualTo(2L);

        JsonNode health = get("/api/pipeline/statistics/source-health?from=2026-10-04&to=2026-10-04&source=RFETM");
        assertThat(health.at("/zone").asText()).isEqualTo("Europe/Madrid");
        assertThat(health.at("/days/0/date").asText()).isEqualTo("2026-10-04");
        assertThat(health.at("/days/0/httpErrors").asLong()).isEqualTo(2);
        assertThat(health.at("/days/0/timeouts").asLong()).isEqualTo(1);
        assertThat(health.at("/days/0/parseErrors").asLong()).isEqualTo(4);
        assertThat(health.at("/totals/0/ingestAttempts").asInt()).isEqualTo(1);
        assertThat(health.at("/totals/0/healthUnknown").asInt()).isZero();

        JsonNode corrections = get("/api/pipeline/statistics/corrections?from=2026-10-04&to=2026-10-04");
        assertThat(corrections.at("/days/0/source").asText()).isEqualTo("RFETM");
        assertThat(corrections.at("/days/0/amendedPlayed").asLong()).isEqualTo(2);
        assertThat(corrections.at("/totals/0/amendedPlayed").asLong()).isEqualTo(2);

        CLOCK.advance(Duration.ofDays(1));
        aggregator.aggregate(DAY);

        Map<String, Object> snapshot = jdbc.queryForMap("SELECT runs, failures, matches_reported, zone "
                + "FROM pipeline.daily_stats WHERE source = 'RFETM' AND stat_date = DATE '2026-10-04'");
        assertThat(snapshot).containsEntry("runs", 1).containsEntry("failures", 0)
                .containsEntry("matches_reported", 0).containsEntry("zone", "Europe/Madrid");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.daily_stats WHERE stat_date = DATE "
                + "'2026-10-04'", Integer.class)).isEqualTo(3);

        JsonNode daily = get("/api/pipeline/statistics/daily?from=2026-10-04&to=2026-10-04&source=RFETM");
        assertThat(daily.at("/rows/0/runs").asInt()).isEqualTo(1);
        JsonNode outcomes = get("/api/pipeline/statistics/runs?from=2026-10-04&to=2026-10-04");
        assertThat(outcomes.at("/days/0/succeeded").asInt()).isEqualTo(1);
    }

    @Test
    void anIngestServiceWithoutHealthCountersLeavesTheHealthUnknown() throws Exception {
        INGEST.on("POST", RUNS, Response.json(202, "{\"runId\":\"ing2\"}"));
        INGEST.on("GET", RUNS + "/ing2", Response.json(200, "{\"runId\":\"ing2\",\"status\":\"SUCCEEDED\","
                + "\"outcome\":\"NO_CHANGES\",\"retryable\":false,\"package\":null,\"error\":null,"
                + "\"stages\":[{\"stage\":\"DOWNLOAD\",\"counters\":{\"seen\":9}}]}"));
        PipelineRun queued = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.RFETM, "2025-2026",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "alice", null, CLOCK.now()));

        executor.execute(queued.id());

        assertThat(jdbc.queryForObject("SELECT http_errors FROM pipeline.pipeline_step WHERE kind = 'INGEST'",
                Long.class)).isNull();
        JsonNode health = get("/api/pipeline/statistics/source-health?from=2026-10-04&to=2026-10-30&source=RFETM");
        assertThat(health.at("/totals/0/healthUnknown").asInt()).isEqualTo(1);
        assertThat(health.at("/totals/0/httpErrors").asLong()).isZero();
    }
}
