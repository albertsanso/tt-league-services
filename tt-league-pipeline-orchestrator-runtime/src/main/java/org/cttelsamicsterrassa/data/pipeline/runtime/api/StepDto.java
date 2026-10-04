package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.time.Instant;
import java.util.UUID;

/** One step attempt. */
public record StepDto(
        UUID runId,
        String kind,
        int attempt,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String externalRef,
        String outcome,
        Boolean retryable,
        ErrorDto error) {
}
