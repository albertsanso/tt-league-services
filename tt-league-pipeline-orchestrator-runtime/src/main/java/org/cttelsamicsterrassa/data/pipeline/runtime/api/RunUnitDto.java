package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One unit of a run: its scope, status, timings, error, ingest run, import job and progress. {@code counters} are the
 * import counters of the unit's platform job; they are null in the summary form (lists and the units of a {@code run}
 * event, and a {@code unit} event of a unit that is still running) and while the unit has no import report.
 */
public record RunUnitDto(
        UUID runId,
        UUID id,
        int ordinal,
        String unitKey,
        String label,
        List<ScopeFilterDto> filters,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        ErrorDto error,
        String ingestRunId,
        UUID importJobId,
        UnitProgressDto progress,
        ImportReportDto counters) {
}
