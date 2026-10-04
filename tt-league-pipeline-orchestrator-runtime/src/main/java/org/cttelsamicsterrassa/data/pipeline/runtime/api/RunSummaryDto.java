package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A run as shown in lists and events. {@code steps} holds the latest attempt per step kind; it is absent in event
 * payloads and in the detail view (which lists every attempt itself).
 */
public record RunSummaryDto(
        UUID id,
        String source,
        String season,
        List<ScopeFilterDto> filters,
        boolean fullSeason,
        String trigger,
        String requestedBy,
        boolean force,
        String status,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        ErrorDto error,
        String ingestRunId,
        UUID importJobId,
        UUID retryOfRunId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<StepStatusDto> steps) {
}
