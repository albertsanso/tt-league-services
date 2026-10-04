package org.cttelsamicsterrassa.data.core.application.importjob.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * An import job and the outcome of each of its seasons. {@code runId} is the caller's run id given at submission;
 * {@code manifestRunId} is the producing run id declared by the uploaded manifest.
 */
public record ImportJobDto(
        UUID importJobId,
        String status,
        String source,
        List<String> seasons,
        String mode,
        String contentSha256,
        String runId,
        String manifestRunId,
        boolean allowPublishedShrink,
        String requestedBy,
        String errorDetail,
        ZonedDateTime createdAt,
        ZonedDateTime startedAt,
        ZonedDateTime finishedAt,
        List<ImportJobSeasonDto> seasonResults) {
}
