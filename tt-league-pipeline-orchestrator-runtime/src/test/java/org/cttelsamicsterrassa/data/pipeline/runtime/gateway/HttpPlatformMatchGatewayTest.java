package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformCompetitionCalendar;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformRoundProgress;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpPlatformMatchGatewayTest {

    private static final String KEY = "platform-secret-key";
    private static final String PROGRESS = HttpPlatformMatchGateway.ROUND_PROGRESS;
    private static final String CALENDAR = HttpPlatformMatchGateway.CALENDAR;

    private final StubHttpServer server = new StubHttpServer();
    private final HttpPlatformMatchGateway gateway = new HttpPlatformMatchGateway(
            HttpClientsConfiguration.restClient(
                    RestClient.builder(), server.baseUrl(), KEY, Duration.ofSeconds(2), Duration.ofSeconds(5)),
            new ObjectMapper());
    private final UUID matchId = UUID.randomUUID();

    @AfterEach
    void stop() {
        server.close();
    }

    private static GatewayException failureOf(Runnable call) {
        try {
            call.run();
        } catch (GatewayException e) {
            return e;
        }
        throw new AssertionError("expected a GatewayException");
    }

    private static final String PROGRESS_BODY = """
            {"source":"FCTT","season":"2026-2027","onlyOpen":false,"today":"2026-10-04","overdueGraceDays":2,
             "unknownField":1,
             "groups":[
              {"competition":"TERCERA","groupNumber":1,"phase":"1a Fase","currentRound":1,"lastCompleteRound":null,
               "rounds":[
                {"round":1,"firstDate":"2026-09-27","lastDate":"2026-09-28","scheduledMatches":1,"playedMatches":2,
                 "postponedMatches":1,"complete":false,"current":true,"open":true},
                {"round":2,"firstDate":null,"lastDate":null,"scheduledMatches":3,"playedMatches":0,
                 "open":false}]},
              {"competition":null,"groupNumber":null,"phase":null,"rounds":[
                {"round":1,"firstDate":"2026-10-03","lastDate":"2026-10-03","scheduledMatches":1,
                 "playedMatches":0,"open":true}]}]}
            """;

    @Test
    void readsRoundProgressWithoutOnlyOpenAndFlattensTheGroups() {
        server.on("GET", PROGRESS, Response.json(200, PROGRESS_BODY));

        PlatformRoundProgress progress = gateway.roundProgress(PipelineSource.FCTT, "2026-2027");

        assertThat(progress.today()).isEqualTo(LocalDate.parse("2026-10-04"));
        assertThat(progress.overdueGraceDays()).isEqualTo(2);
        assertThat(progress.jornadas()).containsExactly(
                new PlatformRoundProgress.PlatformJornada("TERCERA", 1, "1a Fase", 1, LocalDate.parse("2026-09-27"),
                        LocalDate.parse("2026-09-28"), 1, 2, true),
                new PlatformRoundProgress.PlatformJornada("TERCERA", 1, "1a Fase", 2, null, null, 3, 0, false),
                new PlatformRoundProgress.PlatformJornada(null, null, null, 1, LocalDate.parse("2026-10-03"),
                        LocalDate.parse("2026-10-03"), 1, 0, true));
        StubHttpServer.Recorded request = server.requests.get(0);
        assertThat(request.header("X-API-Key")).isEqualTo(KEY);
        assertThat(request.query()).isEqualTo("source=FCTT&season=2026-2027");
    }

    private static final String CALENDAR_BODY = """
            {"source":"FCTT","season":"2026-2027","competition":"TERCERA","today":"2026-10-04","overdueGraceDays":2,
             "groups":[
              {"groupNumber":1,"phase":"1a Fase","rounds":[
               {"round":1,"matches":[
                {"id":"%s","dateTime":"2026-10-03T18:30:00+02:00[Europe/Madrid]","homeTeamName":"CTT A",
                 "awayTeamName":"CTT B","status":"PLAYED","calendarState":"PLAYED","city":"Terrassa",
                 "homeGamesWon":3,"awayGamesWon":1,"winnerTeamName":"CTT A"},
                {"id":"%s","dateTime":null,"homeTeamName":"CTT C","awayTeamName":"CTT D","status":"SCHEDULED",
                 "calendarState":"UNDATED"}]}]},
              {"groupNumber":null,"phase":null,"rounds":[
               {"round":4,"matches":[
                {"id":"%s","dateTime":"2026-10-10T12:00:00Z","homeTeamName":"X","awayTeamName":"Y",
                 "status":"SCHEDULED","calendarState":"AWAITING_RESULT"}]}]}]}
            """;

    @Test
    void readsTheCompetitionCalendarAndFlattensGroupsAndRounds() {
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        server.on("GET", CALENDAR, Response.json(200, CALENDAR_BODY.formatted(matchId, second, third)));

        PlatformCompetitionCalendar calendar = gateway.competitionCalendar(PipelineSource.FCTT, "2026-2027", "TERCERA");

        assertThat(calendar.today()).isEqualTo(LocalDate.parse("2026-10-04"));
        assertThat(calendar.matches()).containsExactly(
                new PlatformCompetitionCalendar.PlatformCalendarMatch(matchId, "TERCERA", 1, "1a Fase", 1,
                        Instant.parse("2026-10-03T16:30:00Z"), "CTT A", "CTT B", "PLAYED", "PLAYED", 3, 1, "CTT A"),
                new PlatformCompetitionCalendar.PlatformCalendarMatch(second, "TERCERA", 1, "1a Fase", 1, null,
                        "CTT C", "CTT D", "SCHEDULED", "UNDATED", null, null, null),
                new PlatformCompetitionCalendar.PlatformCalendarMatch(third, "TERCERA", null, null, 4,
                        Instant.parse("2026-10-10T12:00:00Z"), "X", "Y", "SCHEDULED", "AWAITING_RESULT", null, null, null));
        assertThat(server.requests.get(0).header("X-API-Key")).isEqualTo(KEY);
    }

    @Test
    void encodesCompetitionNamesWithSpacesAccentsAndReservedCharacters() {
        server.on("GET", CALENDAR, Response.json(200, "{\"today\":\"2026-10-04\",\"groups\":[]}"));

        gateway.competitionCalendar(PipelineSource.RFETM, "2026-2027", "Superdivisió Masculina & Co+1");

        assertThat(server.requests.get(0).query()).isEqualTo(
                "source=RFETM&season=2026-2027&competition=Superdivisi%C3%B3%20Masculina%20%26%20Co%2B1");
    }

    @Test
    void forbiddenIsRejectedAndNamesTheApiKey() {
        server.on("GET", PROGRESS, Response.json(403, "{\"message\":\"Forbidden\"}"));

        GatewayException failure = failureOf(() -> gateway.roundProgress(PipelineSource.FCTT, "2026-2027"));

        assertThat(failure.kind()).isEqualTo(Kind.REJECTED);
        assertThat(failure.httpStatus()).isEqualTo(403);
        assertThat(failure.getMessage()).contains("check the configured API key").doesNotContain(KEY);
    }

    @Test
    void serverErrorIsUnavailable() {
        server.on("GET", CALENDAR, Response.json(503, "{\"message\":\"down\"}"));

        GatewayException failure =
                failureOf(() -> gateway.competitionCalendar(PipelineSource.FCTT, "2026-2027", "TERCERA"));

        assertThat(failure.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(failure.httpStatus()).isEqualTo(503);
    }

    @Test
    void connectionFailureIsUnavailable() {
        server.close();

        GatewayException failure = failureOf(() -> gateway.roundProgress(PipelineSource.FCTT, "2026-2027"));

        assertThat(failure.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(failure.httpStatus()).isNull();
    }

    @Test
    void emptyOrNonJsonBodiesAreProtocolErrors() {
        server.on("GET", PROGRESS, Response.empty(200), Response.json(200, "not json"));

        assertThat(failureOf(() -> gateway.roundProgress(PipelineSource.FCTT, "2026-2027")).kind())
                .isEqualTo(Kind.PROTOCOL);
        assertThat(failureOf(() -> gateway.roundProgress(PipelineSource.FCTT, "2026-2027")).kind())
                .isEqualTo(Kind.PROTOCOL);
    }

    @Test
    void missingRequiredFieldsAreProtocolErrors() {
        server.on("GET", PROGRESS,
                Response.json(200, "{\"overdueGraceDays\":2,\"groups\":[]}"),
                Response.json(200, "{\"today\":\"2026-10-04\",\"overdueGraceDays\":2,\"groups\":[{\"competition\":"
                        + "\"T\",\"rounds\":[{\"round\":1,\"scheduledMatches\":1,\"playedMatches\":0}]}]}"));
        server.on("GET", CALENDAR,
                Response.json(200, "{\"today\":\"2026-10-04\",\"groups\":[{\"rounds\":[{\"round\":1,\"matches\":["
                        + "{\"id\":\"" + matchId + "\",\"status\":\"SCHEDULED\"}]}]}]}"));

        assertThat(failureOf(() -> gateway.roundProgress(PipelineSource.FCTT, "2026-2027")).kind())
                .isEqualTo(Kind.PROTOCOL);
        assertThat(failureOf(() -> gateway.roundProgress(PipelineSource.FCTT, "2026-2027")).kind())
                .isEqualTo(Kind.PROTOCOL);
        assertThat(failureOf(() -> gateway.competitionCalendar(PipelineSource.FCTT, "2026-2027", "T")).kind())
                .isEqualTo(Kind.PROTOCOL);
    }

    @Test
    void unparseableValuesAreProtocolErrors() {
        server.on("GET", PROGRESS, Response.json(200,
                "{\"today\":\"yesterday\",\"overdueGraceDays\":2,\"groups\":[]}"));
        server.on("GET", CALENDAR, Response.json(200, "{\"today\":\"2026-10-04\",\"groups\":[{\"rounds\":[{\"round\":1,"
                + "\"matches\":[{\"id\":\"" + matchId + "\",\"dateTime\":\"soon\",\"status\":\"SCHEDULED\","
                + "\"calendarState\":\"UPCOMING\"}]}]}]}"));

        assertThat(failureOf(() -> gateway.roundProgress(PipelineSource.FCTT, "2026-2027")).kind())
                .isEqualTo(Kind.PROTOCOL);
        assertThat(failureOf(() -> gateway.competitionCalendar(PipelineSource.FCTT, "2026-2027", "T")).kind())
                .isEqualTo(Kind.PROTOCOL);
    }
}
