package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A run as shown in lists and events. {@code steps} holds the latest attempt per step kind; it is absent in event
 * payloads and in the detail view (which lists every attempt itself). {@code importJobReused} is the flag of the
 * IMPORT step that submitted the import job of the run; null when unknown (events, no import yet, older runs).
 * {@code units} is the summary form of the run's units (without counters): present in lists and events, absent in the
 * detail view, which lists the detailed units itself. {@code ingestRunId} and {@code importJobId} are those of the
 * single unit of a one-unit run and null otherwise; {@code currentUnitId} is the unit that is running now, if any.
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
        UUID retryOfUnitId,
        Boolean importJobReused,
        UUID currentUnitId,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<RunUnitDto> units,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<StepStatusDto> steps) {
}
