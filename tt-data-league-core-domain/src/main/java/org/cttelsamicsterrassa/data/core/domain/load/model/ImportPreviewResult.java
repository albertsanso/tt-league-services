package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.List;

public record ImportPreviewResult(
        ImportPreviewStatus status,
        List<ImportPreviewFinding> validationFindings,
        List<ImportPreviewProcessingError> processingErrors,
        long filesSeen,
        long itemsDispatched,
        long skipped,
        long processorFailures) {

    public ImportPreviewResult {
        validationFindings = validationFindings == null ? List.of() : List.copyOf(validationFindings);
        processingErrors = processingErrors == null ? List.of() : List.copyOf(processingErrors);
    }

    public static ImportPreviewResult success(List<ImportPreviewFinding> validationFindings,
                                              List<ImportPreviewProcessingError> processingErrors,
                                              long filesSeen,
                                              long itemsDispatched,
                                              long skipped,
                                              long processorFailures) {
        return new ImportPreviewResult(ImportPreviewStatus.SUCCESS, validationFindings, processingErrors,
                filesSeen, itemsDispatched, skipped, processorFailures);
    }

    public static ImportPreviewResult empty(List<ImportPreviewFinding> validationFindings,
                                            List<ImportPreviewProcessingError> processingErrors,
                                            long filesSeen,
                                            long skipped,
                                            long processorFailures) {
        return new ImportPreviewResult(ImportPreviewStatus.EMPTY_RESULT, validationFindings, processingErrors,
                filesSeen, 0, skipped, processorFailures);
    }

    public static ImportPreviewResult failure(List<ImportPreviewFinding> validationFindings,
                                              List<ImportPreviewProcessingError> processingErrors,
                                              long filesSeen,
                                              long itemsDispatched,
                                              long skipped,
                                              long processorFailures) {
        return new ImportPreviewResult(ImportPreviewStatus.FAILURE, validationFindings, processingErrors,
                filesSeen, itemsDispatched, skipped, processorFailures);
    }
}
