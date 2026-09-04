package org.cttelsamicsterrassa.data.core.application.importresource.preview.dto;

import java.util.List;
import java.util.UUID;

public record ImportPreviewResultDto(
        UUID importResourceId,
        String source,
        String season,
        String resourceType,
        String status,
        List<ImportPreviewFindingDto> validationFindings,
        List<ImportPreviewProcessingErrorDto> processingErrors,
        long filesSeen,
        long itemsDispatched,
        long skipped,
        long processorFailures) {
}
