package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.execution.testing.FakeRunClock;
import org.cttelsamicsterrassa.data.pipeline.core.polling.MatchDayRefresh;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettingsProvider;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunScope;
import org.cttelsamicsterrassa.data.pipeline.core.run.RunTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ConflictMode;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.PendingTrigger;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.ScopeType;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun.Outcome;
import org.cttelsamicsterrassa.data.pipeline.core.trigger.port.PendingTriggerRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.cttelsamicsterrassa.data.pipeline.runtime.events.RunEventBroadcaster;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.cttelsamicsterrassa.data.pipeline.core.statistics.StatisticsQueries;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Controllers and security together, with the use cases mocked. */
@WebMvcTest
@Import({SecurityConfiguration.class, ApiExceptionHandler.class, RunsApiWebTest.Settings.class})
class RunsApiWebTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef"; // 48 bytes: HS384
    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    @TestConfiguration
    static class Settings {

        @Bean
        PipelineOrchestratorProperties.Security security() {
            return new PipelineOrchestratorProperties.Security(SECRET, List.of("http://localhost:5173"));
        }

        @Bean
        PipelineOrchestratorProperties.Triggers triggers() {
            return new PipelineOrchestratorProperties.Triggers(ConflictMode.REJECT);
        }

        @Bean
        PipelineOrchestratorProperties.Events events() {
            return new PipelineOrchestratorProperties.Events(java.time.Duration.ofSeconds(15),
                    java.time.Duration.ofMinutes(30), 50);
        }

        @Bean
        RunClock clock() {
            return new FakeRunClock(T0);
        }

        @Bean
        RunDtoMapper mapper(RunClock clock) {
            return new RunDtoMapper(clock);
        }
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    PollingSettingsProvider pollingSettings;
    @MockitoBean
    PollPolicyRepository pollPolicies;
    @MockitoBean
    PollScheduleRepository pollSchedules;
    @MockitoBean
    TriggerRun triggerRun;
    @MockitoBean
    RunQueryService queries;
    @MockitoBean
    PendingTriggerRepository pending;
    @MockitoBean
    RunEventBroadcaster broadcaster;
    @MockitoBean
    MatchDayQueryService matchDayQueries;
    @MockitoBean
    MatchDayActions matchDayActions;
    @MockitoBean
    MatchDayResultsService matchDayResults;
    @MockitoBean
    MatchDayRefresh matchDayRefresh;
    @MockitoBean
    StatisticsQueries statisticsQueries;

    private static String token(String secret, String subject, List<String> permissions, Instant expires)
            throws Exception {
        byte[] key = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        JWSAlgorithm alg = key.length >= 64 ? JWSAlgorithm.HS512 : key.length >= 48 ? JWSAlgorithm.HS384
                : JWSAlgorithm.HS256;
        SignedJWT jwt = new SignedJWT(new JWSHeader(alg), new JWTClaimsSet.Builder().subject(subject)
                .claim("roles", List.of("ADMIN")).claim("permissions", permissions)
                .issueTime(new Date()).expirationTime(Date.from(expires)).build());
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private static String valid(String... permissions) throws Exception {
        return token(SECRET, "alice", List.of(permissions), Instant.now().plusSeconds(600));
    }

    private static PipelineRun run(PipelineSource source) {
        return PipelineRun.queue(UUID.randomUUID(), source, "2025-2026", RunScope.fullSeason(), true,
                RunTrigger.MANUAL, "alice", null, T0);
    }

    private static final String BODY =
            "{\"source\":\"RFETM\",\"season\":\"2025-2026\",\"scopeType\":\"FULL_SEASON\",\"force\":true}";

    @Test
    void rejectsMissingMalformedExpiredAndWrongSignatureTokens() throws Exception {
        mvc.perform(get("/api/pipeline/runs")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/pipeline/runs").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pipeline/runs").header("Authorization", "Bearer "
                + token(SECRET, "alice", List.of(), Instant.now().minusSeconds(3600))))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pipeline/runs").header("Authorization", "Bearer "
                + token("ffffffffffffffffffffffffffffffffffffffffffffffff", "alice", List.of(),
                        Instant.now().plusSeconds(600)))).andExpect(status().isUnauthorized());
    }

    @Test
    void anyValidTokenCanView() throws Exception {
        when(queries.list(any())).thenReturn(new PageDto<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/pipeline/runs").header("Authorization", "Bearer " + valid()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void triggeringNeedsTheMatchesWritePermissionNotJustTheRole() throws Exception {
        mvc.perform(post("/api/pipeline/runs").header("Authorization", "Bearer " + valid())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(triggerRun);
    }

    @Test
    void createsRunsWithTheTokenSubjectAsRequester() throws Exception {
        PipelineRun created = run(PipelineSource.RFETM);
        when(triggerRun.trigger(any())).thenReturn(List.of(new Outcome.Created(created)));

        mvc.perform(post("/api/pipeline/runs").header("Authorization", "Bearer " + valid("matches:write"))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/pipeline/runs/" + created.id()))
                .andExpect(jsonPath("$.results[0].outcome").value("CREATED"))
                .andExpect(jsonPath("$.results[0].run.force").value(true));

        ArgumentCaptor<TriggerRun.Command> captor = ArgumentCaptor.forClass(TriggerRun.Command.class);
        org.mockito.Mockito.verify(triggerRun).trigger(captor.capture());
        assertThat(captor.getValue().requestedBy()).isEqualTo("alice");
        assertThat(captor.getValue().trigger()).isEqualTo(RunTrigger.MANUAL);
        assertThat(captor.getValue().force()).isTrue();
    }

    @Test
    void mapsQueuedRejectedAndUnavailableOutcomesToStatuses() throws Exception {
        String auth = "Bearer " + valid("matches:write");
        UUID active = UUID.randomUUID();
        PendingTrigger pendingTrigger = new PendingTrigger(PipelineSource.RFETM, "2025-2026",
                ScopeType.FULL_SEASON, List.of(), true, "alice", T0);

        when(triggerRun.trigger(any())).thenReturn(List.of(new Outcome.Queued(pendingTrigger, active)));
        mvc.perform(post("/api/pipeline/runs").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isAccepted());

        when(triggerRun.trigger(any())).thenReturn(List.of(
                new Outcome.Rejected(PipelineSource.RFETM, "ACTIVE_RUN", "already running", active)));
        mvc.perform(post("/api/pipeline/runs").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RUN"))
                .andExpect(jsonPath("$.detail").value("already running"))
                .andExpect(jsonPath("$.results[0].activeRunId").value(active.toString()));

        when(triggerRun.trigger(any())).thenReturn(List.of(
                new Outcome.Unavailable(PipelineSource.RFETM, "SCOPE_UNAVAILABLE", "not deployed")));
        mvc.perform(post("/api/pipeline/runs").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SCOPE_UNAVAILABLE"));
    }

    @Test
    void invalidTriggerRequestsAreBadRequests() throws Exception {
        String auth = "Bearer " + valid("matches:write");
        String[] bodies = {
            "{\"source\":\"RFETM\",\"scopeType\":\"FULL_SEASON\"}",
            "{\"source\":\"RFETM\",\"season\":\"2025/2026\",\"scopeType\":\"FULL_SEASON\"}",
            "{\"source\":\"XXX\",\"season\":\"2025-2026\",\"scopeType\":\"FULL_SEASON\"}",
            "{\"source\":\"RFETM\",\"season\":\"2025-2026\",\"scopeType\":\"NOPE\"}",
            "{\"source\":\"RFETM\",\"season\":\"2025-2026\",\"scopeType\":\"GROUP\"}",
            "{\"source\":\"ALL\",\"season\":\"2025-2026\",\"scopeType\":\"GROUP\",\"filters\":[{\"group\":\"G1\"}]}",
            "{\"source\":\"RFETM\",\"season\":\"2025-2026\",\"scopeType\":\"FULL_SEASON\",\"filters\":[{\"group\":\"G1\"}]}",
        };
        for (String body : bodies) {
            mvc.perform(post("/api/pipeline/runs").header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(triggerRun);
    }

    @Test
    void listValidatesPagingAndRange() throws Exception {
        String auth = "Bearer " + valid();
        mvc.perform(get("/api/pipeline/runs?size=101").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"));
        mvc.perform(get("/api/pipeline/runs?from=2026-10-05T00:00:00Z&to=2026-10-04T00:00:00Z")
                .header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/pipeline/runs?from=yesterday").header("Authorization", auth))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pipeline/runs?source=XXX").header("Authorization", auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPassesFiltersThrough() throws Exception {
        when(queries.list(any())).thenReturn(new PageDto<>(List.of(), 2, 5, 0, 0));

        mvc.perform(get("/api/pipeline/runs?source=RFETM&source=FCTT&status=FAILED&page=2&size=5"
                + "&from=2026-10-01T00:00:00Z").header("Authorization", "Bearer " + valid()))
                .andExpect(status().isOk());

        ArgumentCaptor<org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery> captor =
                ArgumentCaptor.forClass(org.cttelsamicsterrassa.data.pipeline.core.run.RunQuery.class);
        org.mockito.Mockito.verify(queries).list(captor.capture());
        assertThat(captor.getValue().sources()).containsExactlyInAnyOrder(PipelineSource.RFETM, PipelineSource.FCTT);
        assertThat(captor.getValue().page()).isEqualTo(2);
        assertThat(captor.getValue().createdFrom()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void unknownRunIsNotFound() throws Exception {
        when(queries.detail(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/pipeline/runs/" + UUID.randomUUID()).header("Authorization", "Bearer " + valid()))
                .andExpect(status().isNotFound());
    }

    @Test
    void pendingTriggersAreListedForAuthenticatedUsers() throws Exception {
        when(pending.findAll()).thenReturn(List.of(new PendingTrigger(PipelineSource.FCTT, "2025-2026",
                ScopeType.FULL_SEASON, List.of(), false, "bob", T0)));

        mvc.perform(get("/api/pipeline/pending-triggers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pipeline/pending-triggers").header("Authorization", "Bearer " + valid()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].source").value("FCTT"));
    }

    @Test
    void corsPreflightIsAnsweredOnlyForConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/pipeline/runs").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/pipeline/runs").header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isForbidden());
    }

    @Test
    void healthStaysPublic() throws Exception {
        // The actuator endpoints are not part of the slice; a permitted path must pass security (404, not 401).
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    @SuppressWarnings("unused")
    private static Object unused() {
        return mock(Object.class);
    }
}
