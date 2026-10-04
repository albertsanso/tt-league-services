package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportJobState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSeasonState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSubmission;
import org.cttelsamicsterrassa.data.pipeline.runtime.gateway.StubHttpServer.Response;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpImportGatewayTest {

    private static final String KEY = "platform-secret-key";
    private static final String JOBS = "/api/v1/administration/import/jobs";
    private static final byte[] ZIP = "PK-zip-bytes-for-upload".getBytes(StandardCharsets.UTF_8);

    private final StubHttpServer server = new StubHttpServer();
    private final HttpImportGateway gateway = new HttpImportGateway(HttpClientsConfiguration.restClient(
            RestClient.builder(), server.baseUrl(), KEY, Duration.ofSeconds(2), Duration.ofSeconds(5)),
            new ObjectMapper());
    private final UUID jobId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @AfterEach
    void stop() {
        server.close();
    }

    private static ArtifactContent zipContent() {
        return new ArtifactContent() {
            @Override
            public long size() {
                return ZIP.length;
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(ZIP);
            }
        };
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
    void uploadsTheZipAsMultipartWithRunIdAndNoShrink() {
        server.on("POST", JOBS, Response.json(202, "{\"importJobId\":\"" + jobId + "\",\"status\":\"QUEUED\","
                + "\"created\":true}"));

        ImportSubmission submission = gateway.submit("run.zip", zipContent(), runId);

        assertThat(submission).isEqualTo(new ImportSubmission(jobId, "QUEUED", true));
        StubHttpServer.Recorded request = server.requests.get(0);
        assertThat(request.header("X-API-Key")).isEqualTo(KEY);
        assertThat(request.header("Content-Type")).startsWith("multipart/form-data");
        String body = new String(request.body(), StandardCharsets.ISO_8859_1);
        assertThat(body).contains("name=\"file\"; filename=\"run.zip\"");
        assertThat(body).contains(new String(ZIP, StandardCharsets.ISO_8859_1));
        assertThat(body).containsPattern("(?s)name=\"runId\".*?\r\n\r\n" + runId);
        assertThat(body).containsPattern("(?s)name=\"allowPublishedShrink\".*?\r\n\r\nfalse");
    }

    @Test
    void aDeduplicatedJobAnswers200() {
        server.on("POST", JOBS, Response.json(200, "{\"importJobId\":\"" + jobId + "\",\"status\":\"SUCCEEDED\","
                + "\"created\":false}"));

        ImportSubmission submission = gateway.submit("run.zip", zipContent(), runId);

        assertThat(submission.created()).isFalse();
        assertThat(submission.importJobId()).isEqualTo(jobId);
    }

    @Test
    void mapsSubmissionFailures() {
        server.on("POST", JOBS,
                Response.json(400, "{\"detail\":\"invalid manifest\"}"),
                Response.json(409, "{\"message\":\"would shrink published actas\"}"),
                Response.json(401, "{\"detail\":\"bad key\"}"),
                Response.json(500, "{}"));

        GatewayException invalid = failureOf(() -> gateway.submit("run.zip", zipContent(), runId));
        GatewayException shrink = failureOf(() -> gateway.submit("run.zip", zipContent(), runId));
        GatewayException unauthorized = failureOf(() -> gateway.submit("run.zip", zipContent(), runId));
        GatewayException unavailable = failureOf(() -> gateway.submit("run.zip", zipContent(), runId));

        assertThat(invalid.kind()).isEqualTo(Kind.REJECTED);
        assertThat(invalid.getMessage()).contains("invalid manifest");
        assertThat(shrink.kind()).isEqualTo(Kind.CONFLICT);
        assertThat(shrink.getMessage()).contains("would shrink published actas");
        assertThat(unauthorized.kind()).isEqualTo(Kind.REJECTED);
        assertThat(unauthorized.getMessage()).contains("check the configured API key").doesNotContain(KEY);
        assertThat(unavailable.kind()).isEqualTo(Kind.UNAVAILABLE);
    }

    @Test
    void readsATwoSeasonJobKeepingTheRawJsonAndIgnoringUnknownFields() {
        String json = "{\"importJobId\":\"" + jobId + "\",\"status\":\"PARTIAL\",\"source\":\"RFETM\","
                + "\"errorDetail\":null,\"futureField\":{\"x\":1},\"seasonResults\":["
                + "{\"season\":\"2024-2025\",\"status\":\"SUCCEEDED\",\"errorDetail\":null,\"result\":{"
                + "\"filesSeen\":4,\"itemsPersisted\":3,\"skipped\":1,\"processorFailures\":0,"
                + "\"scheduledCreated\":2,\"upgradedToPlayed\":1,\"rescheduled\":0,\"partialActas\":1,"
                + "\"invalidActas\":0,\"unresolvedPendingFixtures\":5,\"executionIssues\":[\"late file\"],"
                + "\"findings\":[],\"roundProgress\":[]}},"
                + "{\"season\":\"2025-2026\",\"status\":\"IMPORTING\",\"errorDetail\":\"slow\",\"result\":null}]}";
        server.on("GET", JOBS + "/" + jobId, Response.json(200, json));

        ImportJobState state = gateway.getJob(jobId);

        assertThat(state.rawJson()).isEqualTo(json);
        assertThat(state.status()).isEqualTo("PARTIAL");
        assertThat(state.finished()).isTrue();
        assertThat(state.seasons()).hasSize(2);
        ImportSeasonState first = state.seasons().get(0);
        assertThat(first.season()).isEqualTo("2024-2025");
        assertThat(first.counters().filesSeen()).isEqualTo(4);
        assertThat(first.counters().itemsPersisted()).isEqualTo(3);
        assertThat(first.counters().unresolvedPendingFixtures()).isEqualTo(5);
        assertThat(first.executionIssues()).containsExactly("late file");
        ImportSeasonState second = state.seasons().get(1);
        assertThat(second.counters()).isNull();
        assertThat(second.errorDetail()).isEqualTo("slow");
        assertThat(server.requests.get(0).header("X-API-Key")).isEqualTo(KEY);
    }

    @Test
    void missingCountersCountAsZero() {
        server.on("GET", JOBS + "/" + jobId, Response.json(200, "{\"importJobId\":\"" + jobId
                + "\",\"status\":\"SUCCEEDED\",\"seasonResults\":[{\"season\":\"2025-2026\","
                + "\"status\":\"SUCCEEDED\",\"result\":{\"filesSeen\":2}}]}"));

        ImportJobState state = gateway.getJob(jobId);

        assertThat(state.seasons().get(0).counters().filesSeen()).isEqualTo(2);
        assertThat(state.seasons().get(0).counters().itemsPersisted()).isZero();
    }

    @Test
    void jobErrorsMapToKinds() {
        server.on("GET", JOBS + "/" + jobId, Response.json(404, "{\"detail\":\"unknown job\"}"),
                Response.json(500, "{}"), Response.json(200, "not json"), Response.json(200, "{}"));

        assertThat(failureOf(() -> gateway.getJob(jobId)).kind()).isEqualTo(Kind.NOT_FOUND);
        assertThat(failureOf(() -> gateway.getJob(jobId)).kind()).isEqualTo(Kind.UNAVAILABLE);
        assertThat(failureOf(() -> gateway.getJob(jobId)).kind()).isEqualTo(Kind.PROTOCOL);
        assertThat(failureOf(() -> gateway.getJob(jobId)).kind()).isEqualTo(Kind.PROTOCOL);
    }
}
