package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.IllegalMatchDayTransitionException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayNotFoundException;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayQuery;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayState;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.StaleMatchDayException;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The match-day controller and its security together, with the query service and the actions mocked. */
@WebMvcTest(MatchDaysController.class)
@Import({SecurityConfiguration.class, ApiExceptionHandler.class, RunsApiWebTest.Settings.class})
class MatchDaysApiWebTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String BASE = "/api/pipeline/match-days";

    @Autowired
    MockMvc mvc;
    @MockitoBean
    MatchDayQueryService queries;
    @MockitoBean
    MatchDayActions actions;

    private final UUID dayId = UUID.randomUUID();
    private final UUID matchId = UUID.randomUUID();

    private static String bearer(String... permissions) throws Exception {
        byte[] key = SECRET.getBytes(StandardCharsets.UTF_8);
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS384), new JWTClaimsSet.Builder().subject("alice")
                .claim("roles", List.of("ADMIN")).claim("permissions", List.of(permissions))
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(600))).build());
        jwt.sign(new MACSigner(key));
        return "Bearer " + jwt.serialize();
    }

    private MatchDayDetailDto detail() {
        MatchDaySummaryDto summary = new MatchDaySummaryDto(dayId, "FCTT", "2026-2027", "TERCERA", 1, "1a Fase", 2,
                LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-04"), LocalDate.parse("2026-10-06"), 2,
                "OPEN", null, null, null, Instant.parse("2026-10-04T10:00:00Z"),
                Instant.parse("2026-10-04T10:05:00Z"), Map.of("OVERDUE", 1), 0);
        return new MatchDayDetailDto(summary, List.of(), List.of());
    }

    private void stubDetail() {
        when(queries.detail(dayId)).thenReturn(Optional.of(detail()));
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + dayId)).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/" + dayId + "/close")).andExpect(status().isUnauthorized());
        mvc.perform(put(BASE + "/" + dayId + "/matches/" + matchId + "/ignore")).andExpect(status().isUnauthorized());
        verifyNoInteractions(actions);
    }

    @Test
    void anyAuthenticatedUserCanReadTheListAndTheDetail() throws Exception {
        when(queries.list(any())).thenReturn(new PageDto<>(List.of(), 0, 50, 0, 0));
        stubDetail();

        mvc.perform(get(BASE).header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get(BASE + "/" + dayId).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchDay.id").value(dayId.toString()))
                .andExpect(jsonPath("$.matchDay.state").value("OPEN"))
                .andExpect(jsonPath("$.matchDay.windowEnd").value("2026-10-06"));
    }

    @Test
    void listPassesTheFiltersToTheQuery() throws Exception {
        when(queries.list(any())).thenReturn(new PageDto<>(List.of(), 1, 25, 0, 0));

        mvc.perform(get(BASE).header("Authorization", bearer())
                .param("source", "fctt").param("season", "2026-2027").param("state", "open")
                .param("from", "2026-10-01").param("to", "2026-10-31").param("page", "1").param("size", "25"))
                .andExpect(status().isOk());

        ArgumentCaptor<MatchDayQuery> captor = ArgumentCaptor.forClass(MatchDayQuery.class);
        verify(queries).list(captor.capture());
        MatchDayQuery query = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(query.source()).isEqualTo(PipelineSource.FCTT);
        org.assertj.core.api.Assertions.assertThat(query.season()).isEqualTo("2026-2027");
        org.assertj.core.api.Assertions.assertThat(query.state()).isEqualTo(MatchDayState.OPEN);
        org.assertj.core.api.Assertions.assertThat(query.from()).isEqualTo(LocalDate.parse("2026-10-01"));
        org.assertj.core.api.Assertions.assertThat(query.to()).isEqualTo(LocalDate.parse("2026-10-31"));
        org.assertj.core.api.Assertions.assertThat(query.page()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(query.size()).isEqualTo(25);
    }

    @Test
    void everyMutationNeedsMatchesWrite() throws Exception {
        String auth = bearer();
        String day = BASE + "/" + dayId;
        String ignore = day + "/matches/" + matchId + "/ignore";

        mvc.perform(post(day + "/close").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(post(day + "/reopen").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(put(ignore).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(delete(ignore).header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(post(day + "/notes").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"hello\"}")).andExpect(status().isForbidden());

        verifyNoInteractions(actions);
    }

    @Test
    void closeAndReopenRecordTheTokenSubjectAndNote() throws Exception {
        stubDetail();
        String auth = bearer("matches:write");

        mvc.perform(post(BASE + "/" + dayId + "/close").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"league cancelled it\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.matchDay.id").value(dayId.toString()));
        mvc.perform(post(BASE + "/" + dayId + "/reopen").header("Authorization", auth))
                .andExpect(status().isOk());

        verify(actions).close(dayId, "alice", "league cancelled it");
        verify(actions).reopen(dayId, "alice", null);
    }

    @Test
    void ignoreUnignoreAndNotesRecordTheTokenSubject() throws Exception {
        stubDetail();
        String auth = bearer("matches:write");
        String ignore = BASE + "/" + dayId + "/matches/" + matchId + "/ignore";

        mvc.perform(put(ignore).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"forfeit\"}")).andExpect(status().isOk());
        mvc.perform(delete(ignore).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"   \"}")).andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + dayId + "/notes").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"called the club\",\"matchId\":\"" + matchId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + dayId + "/notes").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"day note\"}"))
                .andExpect(status().isOk());

        verify(actions).ignoreMatch(dayId, matchId, "alice", "forfeit");
        verify(actions).unignoreMatch(dayId, matchId, "alice", null);
        verify(actions).addNote(dayId, matchId, "alice", "called the club");
        verify(actions).addNote(dayId, null, "alice", "day note");
    }

    @Test
    void invalidRequestsAreBadRequests() throws Exception {
        String auth = bearer("matches:write");

        mvc.perform(get(BASE).header("Authorization", auth).param("source", "NOPE"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("source"));
        mvc.perform(get(BASE).header("Authorization", auth).param("season", "2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("season"));
        mvc.perform(get(BASE).header("Authorization", auth).param("state", "WAITING"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("state"));
        mvc.perform(get(BASE).header("Authorization", auth).param("from", "yesterday"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).header("Authorization", auth).param("from", "2026-10-31").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("from"));
        mvc.perform(get(BASE).header("Authorization", auth).param("size", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("size"));
        mvc.perform(get(BASE).header("Authorization", auth).param("size", "201"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE).header("Authorization", auth).param("page", "-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("page"));
        mvc.perform(get(BASE + "/not-a-uuid").header("Authorization", auth)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + dayId + "/notes").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + dayId + "/notes").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + dayId + "/close").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(actions);
    }

    @Test
    void unknownIdsAreNotFound() throws Exception {
        String auth = bearer("matches:write");
        when(queries.detail(dayId)).thenReturn(Optional.empty());
        doThrow(new MatchDayNotFoundException(dayId)).when(actions).close(eq(dayId), any(), any());
        doThrow(new MatchDayNotFoundException(dayId, matchId)).when(actions)
                .ignoreMatch(eq(dayId), eq(matchId), any(), any());

        mvc.perform(get(BASE + "/" + dayId).header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_DAY_NOT_FOUND"));
        mvc.perform(post(BASE + "/" + dayId + "/close").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_DAY_NOT_FOUND"));
        mvc.perform(put(BASE + "/" + dayId + "/matches/" + matchId + "/ignore").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_DAY_NOT_FOUND"));
    }

    @Test
    void illegalTransitionsAndStaleWritesAreConflicts() throws Exception {
        String auth = bearer("matches:write");
        doThrow(new IllegalMatchDayTransitionException(dayId, "is already closed")).when(actions)
                .close(eq(dayId), any(), any());
        doThrow(new StaleMatchDayException("version")).when(actions).reopen(eq(dayId), any(), any());

        mvc.perform(post(BASE + "/" + dayId + "/close").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ILLEGAL_TRANSITION"));
        mvc.perform(post(BASE + "/" + dayId + "/reopen").header("Authorization", auth))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_MATCH_DAY"));
    }
}
