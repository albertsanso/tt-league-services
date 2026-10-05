package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PolicyLevel;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollDecision;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollSchedule;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingPolicySettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettings;
import org.cttelsamicsterrassa.data.pipeline.core.polling.PollingSettingsProvider;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollPolicyRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.PollScheduleRepository;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollPolicyException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.StalePollScheduleException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The polling controller and its security together, with the repositories mocked. */
@WebMvcTest(PollingController.class)
@Import({SecurityConfiguration.class, ApiExceptionHandler.class, RunsApiWebTest.Settings.class,
        PollingApiWebTest.Provider.class})
class PollingApiWebTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String BASE = "/api/pipeline/polling";
    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");

    /** The real provider over the mocked policy repository. */
    @TestConfiguration
    static class Provider {

        @Bean
        PollingSettingsProvider pollingSettingsProvider(PollPolicyRepository policies) {
            return new PollingSettingsProvider(policies, PollingSettings.defaults());
        }
    }

    @Autowired
    MockMvc mvc;
    @MockitoBean
    PollPolicyRepository policies;
    @MockitoBean
    PollScheduleRepository schedules;

    private static String bearer(List<String> roles, String... permissions) throws Exception {
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS384), new JWTClaimsSet.Builder().subject("alice")
                .claim("roles", roles).claim("permissions", List.of(permissions))
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(600))).build());
        jwt.sign(new MACSigner(key));
        return "Bearer " + jwt.serialize();
    }

    private static String admin() throws Exception {
        return bearer(List.of("ADMIN"));
    }

    private static String user(String... permissions) throws Exception {
        return bearer(List.of("USER"), permissions);
    }

    private static final String VALID_BODY = """
            {"matchDay":"PT1H","matchDayStartOffset":"PT2H","dayAfter":"PT3H","daysTwoToSeven":"PT12H",
             "open":"PT24H","overdue":"PT24H","overdueStopAfterDays":14,"fullRefresh":"P7D",
             "noChangeThreshold":4,"version":%d}
            """;

    private PollSchedule stoppedSchedule() {
        PollSchedule fresh = PollSchedule.group(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", "abc",
                new ScopeFilter("tercera", null, null, null, null, List.of(1)),
                new PollDecision(PolicyLevel.OVERDUE, Duration.ofHours(24), Duration.ofHours(24), T0, null, 0), T0);
        return fresh.decided(new PollDecision(PolicyLevel.STOPPED, null, null, null, PollDecision.OVERDUE_LIMIT, 0),
                T0);
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get(BASE + "/policies")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/policies/fctt")).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE + "/policies/fctt")).andExpect(status().isUnauthorized());
        mvc.perform(delete(BASE + "/policies/fctt")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/schedules")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/schedules/" + UUID.randomUUID() + "/resume")).andExpect(status().isUnauthorized());
        verifyNoInteractions(policies, schedules);
    }

    @Test
    void anyAuthenticatedUserReadsTheEffectivePolicies() throws Exception {
        when(policies.find(any())).thenReturn(Optional.empty());
        when(policies.find(PipelineSource.FCTT)).thenReturn(Optional.of(new PollingPolicySettings(
                PipelineSource.FCTT, PollingSettings.defaults(), 3, "bob", T0)));

        mvc.perform(get(BASE + "/policies").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].source").value("RFETM"))
                .andExpect(jsonPath("$[0].overridden").value(false))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].matchDay").value("PT2H"))
                .andExpect(jsonPath("$[2].source").value("FCTT"))
                .andExpect(jsonPath("$[2].overridden").value(true))
                .andExpect(jsonPath("$[2].version").value(3))
                .andExpect(jsonPath("$[2].updatedBy").value("bob"));
        mvc.perform(get(BASE + "/policies/fctt").header("Authorization", user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("FCTT"))
                .andExpect(jsonPath("$.fullRefresh").value("PT168H"))
                .andExpect(jsonPath("$.overdueStopAfterDays").value(21))
                .andExpect(jsonPath("$.noChangeThreshold").value(3));
    }

    @Test
    void anUnknownSourceIsABadRequest() throws Exception {
        mvc.perform(get(BASE + "/policies/xyz").header("Authorization", user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("source"));
    }

    @Test
    void onlyAdminsChangeOrRemoveAPolicy() throws Exception {
        String body = VALID_BODY.formatted(0);
        String operator = user("matches:write");

        mvc.perform(put(BASE + "/policies/fctt").header("Authorization", user())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/policies/fctt").header("Authorization", operator)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/policies/fctt").header("Authorization", operator))
                .andExpect(status().isForbidden());
        verifyNoInteractions(policies);
    }

    @Test
    void anAdminReplacesAPolicyAndGetsTheEffectiveSettingsBack() throws Exception {
        PollingSettings saved = new PollingSettings(Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(3),
                Duration.ofHours(12), Duration.ofHours(24), Duration.ofHours(24), 14, Duration.ofDays(7), 4);
        when(policies.save(eq(PipelineSource.FCTT), eq(saved), eq("alice"), any(), eq(0L)))
                .thenReturn(new PollingPolicySettings(PipelineSource.FCTT, saved, 1, "alice", T0));
        when(policies.find(PipelineSource.FCTT))
                .thenReturn(Optional.of(new PollingPolicySettings(PipelineSource.FCTT, saved, 1, "alice", T0)));

        mvc.perform(put(BASE + "/policies/fctt").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY.formatted(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overridden").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.matchDay").value("PT1H"))
                .andExpect(jsonPath("$.overdueStopAfterDays").value(14))
                .andExpect(jsonPath("$.updatedBy").value("alice"));
        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        verify(policies).save(eq(PipelineSource.FCTT), eq(saved), eq("alice"), at.capture(), eq(0L));
        assertThat(at.getValue()).isEqualTo(T0);
    }

    @Test
    void invalidSettingsAreABadRequest() throws Exception {
        String unordered = VALID_BODY.formatted(0).replace("\"matchDay\":\"PT1H\"", "\"matchDay\":\"PT5H\"");
        String missing = VALID_BODY.formatted(0).replace("\"open\":\"PT24H\",", "");
        String badDuration = VALID_BODY.formatted(0).replace("\"PT1H\"", "\"soon\"");
        String negativeVersion = VALID_BODY.formatted(-1);

        for (String body : List.of(unordered, missing, badDuration, negativeVersion)) {
            mvc.perform(put(BASE + "/policies/fctt").header("Authorization", admin())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(policies);
    }

    @Test
    void aVersionConflictIsA409StalePolicy() throws Exception {
        when(policies.save(any(), any(), any(), any(), eq(2L)))
                .thenThrow(new StalePollPolicyException(PipelineSource.FCTT, 2));

        mvc.perform(put(BASE + "/policies/fctt").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY.formatted(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_POLICY"));
    }

    @Test
    void anAdminRemovesTheOverrideAndTheDefaultsApplyAgain() throws Exception {
        when(policies.delete(PipelineSource.BCNESA)).thenReturn(true);
        when(policies.find(PipelineSource.BCNESA)).thenReturn(Optional.empty());

        mvc.perform(delete(BASE + "/policies/bcnesa").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("BCNESA"))
                .andExpect(jsonPath("$.overridden").value(false))
                .andExpect(jsonPath("$.version").value(0));
        verify(policies).delete(PipelineSource.BCNESA);
    }

    @Test
    void schedulesAreListedWithTheirStateAndFiltered() throws Exception {
        PollSchedule stopped = stoppedSchedule();
        when(schedules.query(PipelineSource.FCTT, "2026-2027")).thenReturn(List.of(stopped));

        mvc.perform(get(BASE + "/schedules").header("Authorization", user())
                .param("source", "fctt").param("season", "2026-2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(stopped.id().toString()))
                .andExpect(jsonPath("$[0].kind").value("GROUP"))
                .andExpect(jsonPath("$[0].level").value("STOPPED"))
                .andExpect(jsonPath("$[0].stopReason").value("OVERDUE_LIMIT"))
                .andExpect(jsonPath("$[0].filter.category").value("tercera"))
                .andExpect(jsonPath("$[0].filter.matchDays[0]").value(1));
        mvc.perform(get(BASE + "/schedules").header("Authorization", user()).param("season", "2026-2028"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resumingNeedsMatchesWrite() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post(BASE + "/schedules/" + id + "/resume").header("Authorization", user()))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/schedules/" + id + "/resume").header("Authorization", admin()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(schedules);
    }

    @Test
    void resumingAStoppedUnitMakesItDueNow() throws Exception {
        PollSchedule stopped = stoppedSchedule();
        when(schedules.findById(stopped.id())).thenReturn(Optional.of(stopped));
        when(schedules.save(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post(BASE + "/schedules/" + stopped.id() + "/resume")
                .header("Authorization", user("matches:write")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("OVERDUE"))
                .andExpect(jsonPath("$.stoppedAt").doesNotExist())
                .andExpect(jsonPath("$.nextRunAt").value("2026-10-04T10:00:00Z"));
    }

    @Test
    void resumeAnswers404ForAnUnknownScheduleAnd409ForOtherCases() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(schedules.findById(unknown)).thenReturn(Optional.empty());
        PollSchedule running = PollSchedule.group(UUID.randomUUID(), PipelineSource.FCTT, "2026-2027", "abc",
                new ScopeFilter("tercera", null, null, null, null, List.of(1)),
                new PollDecision(PolicyLevel.OPEN, Duration.ofHours(24), Duration.ofHours(24), T0, null, 0), T0);
        when(schedules.findById(running.id())).thenReturn(Optional.of(running));
        PollSchedule stopped = stoppedSchedule();
        when(schedules.findById(stopped.id())).thenReturn(Optional.of(stopped));
        when(schedules.save(any())).thenThrow(new StalePollScheduleException(stopped.id(), "changed"));
        String auth = user("matches:write");

        mvc.perform(post(BASE + "/schedules/" + unknown + "/resume").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POLL_SCHEDULE_NOT_FOUND"));
        mvc.perform(post(BASE + "/schedules/" + running.id() + "/resume").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_STOPPED"));
        mvc.perform(post(BASE + "/schedules/" + stopped.id() + "/resume").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_SCHEDULE"));
        verify(schedules, org.mockito.Mockito.times(3)).findById(any());
        verify(schedules).save(any());
        verifyNoMoreInteractions(schedules);
    }
}
