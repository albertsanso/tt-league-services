package org.cttelsamicsterrassa.data.pipeline.runtime.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.GatewayException;
import org.cttelsamicsterrassa.data.pipeline.core.polling.port.IngestStatusGateway;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestMatchDayStatus;
import org.cttelsamicsterrassa.data.pipeline.core.polling.scope.IngestStatusRow;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link IngestStatusGateway} over {@code GET /api/v1/ingest/sources/{source}/match-days-status?season=}. A 404 (no
 * status file yet, or no match days of the season) is an empty result; every other failure is a
 * {@link GatewayException} that never carries the API key. Only the fields the scope join needs are mapped; unknown
 * fields are ignored.
 */
public final class HttpIngestStatusGateway implements IngestStatusGateway {

    static final String STATUS = "/api/v1/ingest/sources/{source}/match-days-status";

    private final RestClient client;
    private final ObjectMapper json;

    public HttpIngestStatusGateway(RestClient client, ObjectMapper json) {
        this.client = client;
        this.json = json;
    }

    @Override
    public Optional<IngestMatchDayStatus> matchDayStatus(PipelineSource source, String season) {
        String raw;
        try {
            raw = client.get().uri(uriBuilder -> uriBuilder.path(STATUS).queryParam("season", "{season}")
                            .build(source.name(), season))
                    .retrieve().body(String.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw GatewayErrors.translate("GET", STATUS, e);
        } catch (RestClientException e) {
            throw GatewayErrors.translate("GET", STATUS, e);
        }
        if (raw == null || raw.isBlank()) {
            throw GatewayErrors.protocol("GET", STATUS, "empty response body", null);
        }
        StatusBody body;
        try {
            body = json.readValue(raw, StatusBody.class);
        } catch (JsonProcessingException e) {
            throw GatewayErrors.protocol("GET", STATUS, "the response is not valid JSON", e);
        }
        if (body.matchDays() == null) {
            throw GatewayErrors.protocol("GET", STATUS, "matchDays is required", null);
        }
        try {
            List<IngestStatusRow> rows = new ArrayList<>();
            for (RowBody row : body.matchDays()) {
                if (row.season() == null || row.matchDay() == null) {
                    throw GatewayErrors.protocol("GET", STATUS, "a row needs season and matchDay", null);
                }
                rows.add(new IngestStatusRow(row.season(), row.category(), row.group(), row.phase(), row.gender(),
                        row.territory(), row.matchDay(), row.status(), dateTime(row.firstMatchAt()),
                        dateTime(row.lastMatchAt())));
            }
            return Optional.of(new IngestMatchDayStatus(source, season, rows));
        } catch (DateTimeException | IllegalArgumentException e) {
            throw GatewayErrors.protocol("GET", STATUS, "unreadable value: " + e.getMessage(), e);
        }
    }

    private static LocalDateTime dateTime(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StatusBody(List<RowBody> matchDays) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RowBody(
            String season,
            String category,
            String group,
            String phase,
            String gender,
            String territory,
            Integer matchDay,
            String status,
            String firstMatchAt,
            String lastMatchAt) {
    }
}
