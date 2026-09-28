package org.cttelsamicsterrassa.data.core.application.importresource.process.dto;

import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewFindingDto;
import org.cttelsamicsterrassa.data.core.application.importresource.preview.dto.ImportPreviewProcessingErrorDto;

import java.util.List;
import java.util.UUID;

public record ImportProcessResultDto(
        UUID importResourceId,
        String source,
        String season,
        String resourceType,
        String status,
        List<ImportPreviewFindingDto> findings,
        List<ImportPreviewProcessingErrorDto> processingErrors,
    long filesSeen,
    long itemsPersisted,
    long skipped,
    long processorFailures,
    long elapsedMillis,
    long persistenceWrites,
    List<String> executionIssues,
    List<String> postProcessingOutcomes,
    long scheduledCreated,
    long upgradedToPlayed,
    long rescheduled,
    long partialActas,
    long invalidActas,
    long unresolvedPendingFixtures) {

public ImportProcessResultDto(UUID importResourceId, String source, String season, String resourceType,
                              String status, List<ImportPreviewFindingDto> findings,
                              List<ImportPreviewProcessingErrorDto> processingErrors, long filesSeen,
                              long itemsPersisted, long skipped, long processorFailures) {
    this(importResourceId, source, season, resourceType, status, findings, processingErrors, filesSeen,
            itemsPersisted, skipped, processorFailures, 0, 0, List.of(), List.of(),
            0, 0, 0, 0, 0, 0);
}

public ImportProcessResultDto(UUID importResourceId, String source, String season, String resourceType,
                              String status, List<ImportPreviewFindingDto> findings,
                              List<ImportPreviewProcessingErrorDto> processingErrors, long filesSeen,
                              long itemsPersisted, long skipped, long processorFailures, long elapsedMillis,
                              long persistenceWrites, List<String> executionIssues,
                              List<String> postProcessingOutcomes) {
    this(importResourceId, source, season, resourceType, status, findings, processingErrors, filesSeen,
            itemsPersisted, skipped, processorFailures, elapsedMillis, persistenceWrites,
            executionIssues, postProcessingOutcomes, 0, 0, 0, 0, 0, 0);
}
}
