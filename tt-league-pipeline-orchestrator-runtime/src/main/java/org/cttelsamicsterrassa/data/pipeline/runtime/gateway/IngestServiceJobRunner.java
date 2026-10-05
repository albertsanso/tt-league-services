package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.FetchedPackage;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException.Kind;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestMode;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunRequest;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.IngestRunState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.PackageSink;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.StoredArtifact;
import org.cttelsamicsterrassa.data.pipeline.core.run.IngestHealth;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link IngestGateway} over the {@code tt-league-ingest-rest} API. */
public final class IngestServiceJobRunner implements IngestGateway {

    private static final String RUNS = "/api/v1/ingest/runs";
    private static final String RUN = RUNS + "/{id}";
    private static final String PACKAGE = RUN + "/package";
    private static final String CHECKSUM_HEADER = "X-Content-SHA256";
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final List<String> STAGES = List.of("download", "parse", "package");
    private static final int MAX_RUN_ID = 64;
    private static final Set<String> HEALTH_COUNTERS = Set.of("http_errors", "timeouts", "parse_errors");
    private static final Set<String> PARSE_STAGES = Set.of("parse", "teams");

    private final RestClient client;

    public IngestServiceJobRunner(RestClient client) {
        this.client = client;
    }

    @Override
    public String startRun(IngestRunRequest request) {
        ResponseEntity<RunIdBody> response;
        try {
            response = client.post().uri(RUNS).contentType(MediaType.APPLICATION_JSON)
                    .body(StartBody.from(request)).retrieve().toEntity(RunIdBody.class);
        } catch (RestClientException e) {
            throw GatewayErrors.translate("POST", RUNS, e);
        }
        RunIdBody body = response.getBody();
        if (response.getStatusCode().value() != 202 || body == null || body.runId() == null
                || body.runId().isBlank() || body.runId().length() > MAX_RUN_ID) {
            throw GatewayErrors.protocol("POST", RUNS,
                    "expected 202 with a runId of 1 to " + MAX_RUN_ID + " characters", null);
        }
        return body.runId();
    }

    @Override
    public IngestRunState getRun(String ingestRunId) {
        RunStateBody body;
        try {
            body = client.get().uri(RUN, ingestRunId).retrieve().body(RunStateBody.class);
        } catch (RestClientException e) {
            throw GatewayErrors.translate("GET", RUN, e);
        }
        if (body == null || body.status() == null) {
            throw GatewayErrors.protocol("GET", RUN, "the run state has no status", null);
        }
        return new IngestRunState(ingestRunId, body.status(), body.outcome(), body.retryable(),
                body.packageRef() != null, body.error(), health(body.stages()));
    }

    /**
     * The source health of a run: HTTP errors and timeouts summed over every stage, parse errors summed over the
     * parse and teams stages as {@code parse_errors + invalid}. Null when no stage carries the health counters (an
     * ingest service from before FEAT-00113); a negative value is a protocol error.
     */
    private static IngestHealth health(List<StageBody> stages) {
        if (stages == null) {
            return null;
        }
        boolean reported = false;
        long httpErrors = 0;
        long timeouts = 0;
        long parseErrors = 0;
        for (StageBody stage : stages) {
            Map<String, Long> counters = stage.counters();
            if (counters == null) {
                continue;
            }
            reported |= counters.keySet().stream().anyMatch(HEALTH_COUNTERS::contains);
            httpErrors += counter(counters, "http_errors");
            timeouts += counter(counters, "timeouts");
            if (PARSE_STAGES.contains(String.valueOf(stage.stage()).toLowerCase(Locale.ROOT))) {
                parseErrors += counter(counters, "parse_errors") + counter(counters, "invalid");
            }
        }
        return reported ? new IngestHealth(httpErrors, timeouts, parseErrors) : null;
    }

    private static long counter(Map<String, Long> counters, String name) {
        Long value = counters.get(name);
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw GatewayErrors.protocol("GET", RUN, "the stage counter " + name + " is negative", null);
        }
        return value;
    }

    @Override
    public FetchedPackage fetchPackage(String ingestRunId, PackageSink sink) {
        try {
            return client.get().uri(PACKAGE, ingestRunId).accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                    .exchange((request, response) -> {
                        HttpStatusCode status = response.getStatusCode();
                        if (status.isError()) {
                            throw GatewayErrors.fromStatus("GET", PACKAGE, status.value(), errorBody(response.getBody()));
                        }
                        if (status.value() != 200) {
                            throw GatewayErrors.protocol("GET", PACKAGE, "expected HTTP 200 but got "
                                    + status.value(), null);
                        }
                        String declared = response.getHeaders().getFirst(CHECKSUM_HEADER);
                        if (declared == null || !SHA256.matcher(declared).matches()) {
                            throw GatewayErrors.protocol("GET", PACKAGE, CHECKSUM_HEADER
                                    + " header is missing or not 64 lower-case hex characters", null);
                        }
                        try (InputStream body = response.getBody()) {
                            StoredArtifact stored = sink.write(body);
                            return new FetchedPackage(declared, stored);
                        }
                    });
        } catch (RestClientException e) {
            throw GatewayErrors.translate("GET", PACKAGE, e);
        }
    }

    private static String errorBody(InputStream body) throws IOException {
        return new String(body.readNBytes(4096), StandardCharsets.UTF_8);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record StartBody(
            String source,
            String season,
            List<String> stages,
            String mode,
            boolean force,
            boolean allowPublishedShrink,
            List<ScopeBody> scopes) {

        static StartBody from(IngestRunRequest request) {
            List<ScopeBody> scopes = request.scope().isFullSeason()
                    ? null
                    : request.scope().filters().stream().map(ScopeBody::from).toList();
            return new StartBody(request.source().name(), request.season(), STAGES, mode(request.mode()),
                    request.force(), false, scopes);
        }

        private static String mode(IngestMode mode) {
            return mode.name().toLowerCase(Locale.ROOT);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private record ScopeBody(
            String category, String group, String phase, String territory, String gender, List<Integer> matchDays) {

        static ScopeBody from(ScopeFilter filter) {
            return new ScopeBody(filter.category(), filter.group(), filter.phase(), filter.territory(),
                    filter.gender(), filter.matchDays());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RunIdBody(String runId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RunStateBody(
            String status,
            String outcome,
            boolean retryable,
            @JsonProperty("package") Object packageRef,
            String error,
            List<StageBody> stages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StageBody(String stage, Map<String, Long> counters) {
    }
}
