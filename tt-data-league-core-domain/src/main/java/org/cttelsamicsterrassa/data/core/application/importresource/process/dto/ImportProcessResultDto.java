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
        long processorFailures) {
}
