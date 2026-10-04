package org.cttelsamicsterrassa.data.pipeline.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.RecordingDispatcher;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunError;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScopeType;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.cttelsamicsterrassa.data.pipeline.runtime.persistence.PostgresTestConfiguration;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The runs API against a real database; no HTTP call leaves the process because the dispatcher is a recorder. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "tt.pipeline.platform.base-url=http://localhost:8080",
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
            "tt.pipeline.security.jwt-secret=0123456789abcdef0123456789abcdef"
        })
@Import({PostgresTestConfiguration.class, RunsApiIntegrationTest.Recording.class})
@Testcontainers(disabledWithoutDocker = true)
class RunsApiIntegrationTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String BODY =
            "{\"source\":\"RFETM\",\"season\":\"2025-2026\",\"scopeType\":\"FULL_SEASON\",\"force\":true}";

    @TestConfiguration(proxyBeanMethods = false)
    static class Recording {

        @Bean
        @Primary
        RecordingDispatcher recordingDispatcher() {
            return new RecordingDispatcher();
        }
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TriggerRun triggerRun;
    @Autowired
    PipelineRunRepository runs;
    @Autowired
    RunObserver observer;
    @Autowired
    RunDispatcher dispatcher;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE pipeline.match_day, pipeline.pending_trigger, pipeline.import_report, pipeline.run_artifact, "
                + "pipeline.pipeline_step, pipeline.pipeline_run CASCADE");
    }

    private static String token(String... permissions) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), new JWTClaimsSet.Builder()
                .subject("alice").claim("permissions", List.of(permissions))
                .expirationTime(Date.from(Instant.now().plusSeconds(600))).build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void protectedEndpointsNeedATokenAndHealthStaysPublic() {
        assertThat(call(HttpMethod.GET, "/api/pipeline/runs", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/actuator/health", null, null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void postStoresAManualForcedRunThenConflictsAndReadsBack() throws Exception {
        String token = token("matches:write");

        ResponseEntity<String> created = call(HttpMethod.POST, "/api/pipeline/runs", token, BODY);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(jdbc.queryForMap("SELECT trigger, requested_by, force FROM pipeline.pipeline_run"))
                .containsEntry("trigger", "MANUAL").containsEntry("requested_by", "alice")
                .containsEntry("force", true);

        assertThat(call(HttpMethod.POST, "/api/pipeline/runs", token, BODY).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        String list = call(HttpMethod.GET, "/api/pipeline/runs?source=RFETM", token, null).getBody();
        assertThat(list).contains("\"totalItems\":1").contains("\"requestedBy\":\"alice\"");
        String id = jdbc.queryForObject("SELECT id::text FROM pipeline.pipeline_run", String.class);
        assertThat(call(HttpMethod.GET, "/api/pipeline/runs/" + id, token, null).getBody())
                .contains("\"id\":\"" + id + "\"").contains("\"issues\":[]");
    }

    @Test
    void queuedTriggerIsStoredThenLaunchedWhenTheActiveRunEnds() {
        TriggerRun.Command command = new TriggerRun.Command(List.of(PipelineSource.RFETM), "2025-2026",
                ScopeType.FULL_SEASON, List.of(), false, RunTrigger.MANUAL, "alice", ConflictMode.QUEUE);
        PipelineRun first = ((Outcome.Created) triggerRun.trigger(command).get(0)).run();

        assertThat(triggerRun.trigger(command).get(0)).isInstanceOf(Outcome.Queued.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.pending_trigger", Integer.class)).isEqualTo(1);
        assertThat(triggerRun.trigger(command).get(0)).isInstanceOfSatisfying(Outcome.Rejected.class,
                rejected -> assertThat(rejected.code()).isEqualTo("PENDING_EXISTS"));

        PipelineRun failed = runs.update(first.fail(new RunError("E", "boom"), Instant.now()));
        observer.runChanged(failed);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline.pending_trigger", Integer.class)).isZero();
        assertThat(runs.findActiveBySource(PipelineSource.RFETM)).isPresent().get()
                .extracting(PipelineRun::id).isNotEqualTo(first.id());
        assertThat(dispatcher).isInstanceOf(RecordingDispatcher.class);
    }
}
