package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.util.UUID;

/** One step attempt of a unit. {@code importJobReused} is set on IMPORT steps: the platform returned an existing job. */
public record StepDto(
        UUID runId,
        UUID unitId,
        String kind,
        int attempt,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String externalRef,
        String outcome,
        Boolean retryable,
        ErrorDto error,
        Health health,
        Boolean importJobReused) {

    /** Source failures an INGEST attempt reported; null when unknown or not an INGEST step. */
    public record Health(long httpErrors, long timeouts, long parseErrors) {
    }
}
