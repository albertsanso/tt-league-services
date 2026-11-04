package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestStatusRow;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpIngestStatusGatewayTest {

    private static final String KEY = "ingest-secret-key";
    private static final String PATH = "/api/v1/ingest/sources/FCTT/match-days-status";

    private final StubHttpServer server = new StubHttpServer();
    private final HttpIngestStatusGateway gateway = new HttpIngestStatusGateway(
            HttpClientsConfiguration.restClient(
                    RestClient.builder(), server.baseUrl(), KEY, Duration.ofSeconds(2), Duration.ofSeconds(5)),
            new ObjectMapper());

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

    private static final String BODY = """
            {"version":1,"source":"FCTT","evaluatedAt":"2026-10-04T12:00","seasons":["2026-2027"],
             "summary":{"matchDays":2},
             "matchDays":[
              {"season":"2026-2027","category":"tercera","group":"G1","phase":"1a Fase","gender":"male",
               "territory":"Barcelona","matchDay":3,"status":"scheduled","matches":4,"played":0,"reported":0,
               "firstMatchAt":"2026-10-03T10:00","lastMatchAt":"2026-10-04T18:30","file":"x.html",
               "contentUpdatedAt":"2026-10-04T10:00:00+00:00"},
              {"season":"2026-2027","category":"tercera","group":"G1","phase":"","gender":"","territory":null,
               "matchDay":4,"status":"future","firstMatchAt":null,"lastMatchAt":null,"unknown":1}]}
            """;

    @Test
    void mapsTheRowsAndSendsTheSeasonWithTheKey() {
        server.on("GET", PATH, Response.json(200, BODY));

        Optional<IngestMatchDayStatus> status = gateway.matchDayStatus(PipelineSource.FCTT, "2026-2027");

        assertThat(status).isPresent();
        assertThat(status.get().source()).isEqualTo(PipelineSource.FCTT);
        assertThat(status.get().season()).isEqualTo("2026-2027");
        assertThat(status.get().rows()).containsExactly(
                new IngestStatusRow("2026-2027", "tercera", "G1", "1a Fase", "male", "Barcelona", 3, "scheduled",
                        LocalDateTime.parse("2026-10-03T10:00"), LocalDateTime.parse("2026-10-04T18:30")),
                new IngestStatusRow("2026-2027", "tercera", "G1", null, null, null, 4, "future", null, null));
        StubHttpServer.Recorded request = server.requests.get(0);
        assertThat(request.header("X-API-Key")).isEqualTo(KEY);
        assertThat(request.query()).isEqualTo("season=2026-2027");
    }

    @Test
    void aNotFoundStatusIsEmpty() {
        server.on("GET", PATH, Response.json(404, "{\"detail\":\"no match-day status for FCTT yet\"}"));

        assertThat(gateway.matchDayStatus(PipelineSource.FCTT, "2026-2027")).isEmpty();
    }

    @Test
    void unauthorizedAndServerErrorsAreGatewayFailuresWithoutTheKey() {
        server.on("GET", PATH, Response.json(401, "{\"detail\":\"invalid key\"}"),
                Response.json(503, "{\"detail\":\"busy\"}"));

        GatewayException unauthorized = failureOf(() -> gateway.matchDayStatus(PipelineSource.FCTT, "2026-2027"));
        GatewayException unavailable = failureOf(() -> gateway.matchDayStatus(PipelineSource.FCTT, "2026-2027"));

        assertThat(unauthorized.kind()).isEqualTo(Kind.REJECTED);
        assertThat(unauthorized.httpStatus()).isEqualTo(401);
        assertThat(unauthorized.getMessage()).contains("check the configured API key").doesNotContain(KEY);
        assertThat(unavailable.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(unavailable.getMessage()).doesNotContain(KEY);
    }

    @Test
    void aBadRequestIsRejected() {
        server.on("GET", PATH, Response.json(400, "{\"detail\":\"malformed season\"}"));

        GatewayException failure = failureOf(() -> gateway.matchDayStatus(PipelineSource.FCTT, "2026-2027"));

        assertThat(failure.kind()).isEqualTo(Kind.REJECTED);
        assertThat(failure.getMessage()).contains("malformed season");
    }

    @Test
    void malformedBodiesAreProtocolFailures() {
        server.on("GET", PATH, Response.json(200, "not json"), Response.json(200, "{\"summary\":{}}"),
                Response.json(200, "{\"matchDays\":[{\"season\":\"2026-2027\",\"category\":\"t\"}]}"),
                Response.json(200, "{\"matchDays\":[{\"season\":\"2026-2027\",\"matchDay\":1,"
                        + "\"firstMatchAt\":\"tomorrow\"}]}"));

        for (int i = 0; i < 4; i++) {
            GatewayException failure = failureOf(() -> gateway.matchDayStatus(PipelineSource.FCTT, "2026-2027"));
            assertThat(failure.kind()).isEqualTo(Kind.PROTOCOL);
        }
    }

    @Test
    void anUnreachableServerIsUnavailable() {
        server.close();

        GatewayException failure = failureOf(() -> gateway.matchDayStatus(PipelineSource.FCTT, "2026-2027"));

        assertThat(failure.kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(failure.getMessage()).doesNotContain(KEY);
    }
}
