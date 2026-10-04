package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ArtifactContent;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportCounters;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportGateway;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportJobState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSeasonState;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.ImportSubmission;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link ImportGateway} over the platform import jobs API. */
public final class HttpImportGateway implements ImportGateway {

    private static final String JOBS = "/api/v1/administration/import/jobs";
    private static final String JOB = JOBS + "/{id}";

    private final RestClient client;
    private final ObjectMapper json;

    public HttpImportGateway(RestClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public ImportSubmission submit(String fileName, ArtifactContent content, UUID clientRunId) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ArtifactResource(fileName, content));
        parts.add("runId", clientRunId.toString());
        parts.add("allowPublishedShrink", "false");
        ResponseEntity<SubmissionBody> response;
        try {
            response = client.post().uri(JOBS).contentType(MediaType.MULTIPART_FORM_DATA).body(parts).retrieve()
                    .toEntity(SubmissionBody.class);
        } catch (RestClientException e) {
            throw GatewayErrors.translate("POST", JOBS, e);
        }
        int status = response.getStatusCode().value();
        SubmissionBody body = response.getBody();
        if ((status != 202 && status != 200) || body == null || body.importJobId() == null) {
            throw GatewayErrors.protocol("POST", JOBS, "expected 202 or 200 with an importJobId but got HTTP "
                    + status, null);
        }
        return new ImportSubmission(body.importJobId(), body.status(), body.created());
    }

    @Override
    public ImportJobState getJob(UUID importJobId) {
        String raw;
        try {
            raw = client.get().uri(JOB, importJobId).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw GatewayErrors.translate("GET", JOB, e);
        }
        if (raw == null || raw.isBlank()) {
            throw GatewayErrors.protocol("GET", JOB, "empty response body", null);
        }
        JobBody body;
        try {
            body = json.readValue(raw, JobBody.class);
        } catch (JsonProcessingException e) {
            throw GatewayErrors.protocol("GET", JOB, "the job is not valid JSON", e);
        }
        if (body.status() == null) {
            throw GatewayErrors.protocol("GET", JOB, "the job has no status", null);
        }
        List<ImportSeasonState> seasons = body.seasonResults() == null
                ? List.of()
                : body.seasonResults().stream().map(HttpImportGateway::toSeason).toList();
        return new ImportJobState(importJobId, body.status(), body.errorDetail(), seasons, raw);
    }

    private static ImportSeasonState toSeason(SeasonBody season) {
        ResultBody result = season.result();
        ImportCounters counters = result == null
                ? null
                : new ImportCounters(zero(result.filesSeen()), zero(result.itemsPersisted()),
                        zero(result.skipped()), zero(result.processorFailures()), zero(result.scheduledCreated()),
                        zero(result.upgradedToPlayed()), zero(result.rescheduled()), zero(result.partialActas()),
                        zero(result.invalidActas()), zero(result.unresolvedPendingFixtures()));
        return new ImportSeasonState(season.season(), season.status(), season.errorDetail(), counters,
                result == null ? List.of() : result.executionIssues());
    }

    private static long zero(Long value) {
        return value == null ? 0 : value;
    }

    /** The stored ZIP as a multipart file part that can be re-read for every attempt. */
    private static final class ArtifactResource extends AbstractResource {

        private final String fileName;
        private final ArtifactContent content;

        ArtifactResource(String fileName, ArtifactContent content) {
            this.fileName = fileName;
            this.content = content;
        }

        @Override
        public String getDescription() {
            return "artifact " + fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }

        @Override
        public long contentLength() {
            return content.size();
        }

        @Override
        public InputStream getInputStream() {
            return content.open();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SubmissionBody(UUID importJobId, String status, boolean created) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record JobBody(String status, String errorDetail, List<SeasonBody> seasonResults) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SeasonBody(String season, String status, String errorDetail, ResultBody result) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResultBody(
            Long filesSeen,
            Long itemsPersisted,
            Long skipped,
            Long processorFailures,
            Long scheduledCreated,
            Long upgradedToPlayed,
            Long rescheduled,
            Long partialActas,
            Long invalidActas,
            Long unresolvedPendingFixtures,
            List<String> executionIssues) {
    }
}
