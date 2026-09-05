package org.cttelsamicsterrassa.data.core.application.importresource.process.dto;

import java.util.UUID;

/**
 * Wire shape for both the {@code 202 Accepted} response returned when an import run is queued and
 * the polling response returned while the caller checks on it. {@code result} stays {@code null}
 * until {@code status} reaches a terminal value.
 */
public record ImportRunStatusDto(
        UUID runId,
        UUID importResourceId,
        String source,
        String season,
        String status,
        Long processed,
        Long total,
        Double percentage,
        long skipped,
        long errorCount,
        String errorDetail,
        ImportProcessResultDto result) {
}
