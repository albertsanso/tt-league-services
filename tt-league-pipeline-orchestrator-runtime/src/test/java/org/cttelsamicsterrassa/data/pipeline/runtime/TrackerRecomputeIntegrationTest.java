package org.cttelsamicsterrassa.data.pipeline.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import org.cttelsamicsterrassa.data.pipeline.runtime.persistence.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A run that reaches a final state makes the tracker read the platform (a stub server) and write match days and
 * matches to a real database, with the run recorded as the one that saw the result.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "tt.pipeline.platform.api-key=test-platform-key",
            "tt.pipeline.platform.connect-timeout=PT2S",
            "tt.pipeline.platform.read-timeout=PT5S",
            "tt.pipeline.platform.poll-interval=PT1S",
            "tt.pipeline.ingest.base-url=http://localhost:8000",
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
@Import({PostgresTestConfiguration.class, TrackerRecomputeIntegrationTest.Recording.class})
@Testcontainers(disabledWithoutDocker = true)
class TrackerRecomputeIntegrationTest {

    /** Started with the context, so a skipped class (no Docker) never opens a port. */
    private static StubHttpServer platform;
    private static final String PROGRESS = "/api/v1/match/round-progress";
    private static final String CALENDAR = "/api/v1/match/calendar";

    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {

        @Bean
        @Primary
        RecordingDispatcher recordingDispatcher() {
            return new RecordingDispatcher();
        }
    }

    @DynamicPropertySource
    static void platformUrl(DynamicPropertyRegistry registry) {
        platform = new StubHttpServer();
        registry.add("tt.pipeline.platform.base-url", () -> platform.baseUrl().toString());
    }

    @AfterAll
    static void stopPlatform() {
        if (platform != null) {
            platform.close();
        }
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PipelineRunRepository runs;
    @Autowired
    RunObserver observer;
    @Autowired
    RunDispatcher dispatcher;

    private final UUID reportedMatch = UUID.randomUUID();
    private final UUID overdueMatch = UUID.randomUUID();

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE pipeline.match_day, pipeline.pending_trigger, pipeline.import_report, "
                + "pipeline.run_artifact, pipeline.pipeline_step, pipeline.pipeline_run CASCADE");
        platform.requests.clear();
    }

    private PipelineRun succeededRun() {
        Instant t0 = Instant.parse("2026-10-04T10:00:00Z");
        PipelineRun run = runs.create(PipelineRun.queue(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027",
                RunScope.fullSeason(), false, RunTrigger.MANUAL, "alice", null, t0));
        run = runs.update(run.startIngest("ingest-1", t0.plusSeconds(1)));
        run = runs.update(run.packed(t0.plusSeconds(2)));
        run = runs.update(run.startImport(UUID.randomUUID(), t0.plusSeconds(3)));
        return runs.update(run.succeed(t0.plusSeconds(60)));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("condition not met within 15 seconds");
            }
            Thread.sleep(100);
        }
    }

    @Test
    void aFinishedRunProducesMatchDaysAndMatchesWithTheRunThatSawTheResult() throws Exception {
        platform.onRepeating("GET", PROGRESS, Response.json(200, """
                {"today":"2026-10-04","overdueGraceDays":2,"groups":[{"competition":"TERCERA","groupNumber":1,
                 "phase":"1a Fase","rounds":[{"round":1,"firstDate":"2026-10-03","lastDate":"2026-10-04",
                 "scheduledMatches":1,"playedMatches":1,"open":true}]}]}"""));
        platform.onRepeating("GET", CALENDAR, Response.json(200, """
                {"today":"2026-10-04","groups":[{"groupNumber":1,"phase":"1a Fase","rounds":[{"round":1,"matches":[
                 {"id":"%s","dateTime":"2026-10-03T18:00:00+02:00","homeTeamName":"A","awayTeamName":"B",
                  "status":"PLAYED","calendarState":"PLAYED"},
                 {"id":"%s","dateTime":"2026-10-04T12:00:00+02:00","homeTeamName":"C","awayTeamName":"D",
                  "status":"SCHEDULED","calendarState":"OVERDUE"}]}]}]}""".formatted(reportedMatch, overdueMatch)));
        assertThat(dispatcher).isInstanceOf(RecordingDispatcher.class);

        PipelineRun run = succeededRun();
        observer.runChanged(run);

        await(() -> jdbc.queryForObject("SELECT count(*) FROM pipeline.match_tracking", Integer.class) == 2);

        Map<String, Object> day = jdbc.queryForMap(
                "SELECT competition, group_number, phase, round, state, grace_days FROM pipeline.match_day");
        assertThat(day).containsEntry("competition", "TERCERA").containsEntry("group_number", 1)
                .containsEntry("phase", "1a Fase").containsEntry("round", 1).containsEntry("state", "OPEN")
                .containsEntry("grace_days", 2);
        Map<String, Object> reported = jdbc.queryForMap(
                "SELECT status, reported_run_id, reported_at FROM pipeline.match_tracking WHERE match_id = ?",
                reportedMatch);
        assertThat(reported).containsEntry("status", "REPORTED").containsEntry("reported_run_id", run.id());
        assertThat(((java.sql.Timestamp) reported.get("reported_at")).toInstant()).isEqualTo(run.finishedAt());
        assertThat(jdbc.queryForObject("SELECT status FROM pipeline.match_tracking WHERE match_id = ?",
                String.class, overdueMatch)).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForList("SELECT kind FROM pipeline.match_day_event ORDER BY kind", String.class))
                .containsExactly("MATCH_REPORTED", "OPENED");
        assertThat(platform.requestsTo("GET", PROGRESS)).hasSize(1);
        assertThat(platform.requestsTo("GET", PROGRESS).get(0).header("X-API-Key")).isEqualTo("test-platform-key");
        assertThat(List.of(platform.requestsTo("GET", CALENDAR).get(0).query())).singleElement().asString()
                .contains("competition=TERCERA");
    }
}
