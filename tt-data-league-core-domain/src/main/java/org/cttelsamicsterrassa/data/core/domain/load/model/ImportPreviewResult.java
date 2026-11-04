package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.List;

public record ImportPreviewResult(
        ImportPreviewStatus status,
        List<ImportPreviewFinding> validationFindings,
        List<ImportPreviewProcessingError> processingErrors,
        long filesSeen,
        long itemsDispatched,
        long skipped,
        long processorFailures,
        ImportPreviewClassification classification) {

    public ImportPreviewResult {
        validationFindings = validationFindings == null ? List.of() : List.copyOf(validationFindings);
        processingErrors = processingErrors == null ? List.of() : List.copyOf(processingErrors);
        classification = classification == null ? ImportPreviewClassification.empty() : classification;
    }

    public static ImportPreviewResult success(List<ImportPreviewFinding> validationFindings,
                                              List<ImportPreviewProcessingError> processingErrors,
                                              long filesSeen,
                                              long itemsDispatched,
                                              long skipped,
                                              long processorFailures) {
        return success(validationFindings, processingErrors, filesSeen, itemsDispatched, skipped,
                processorFailures, ImportPreviewClassification.empty());
    }

    public static ImportPreviewResult success(List<ImportPreviewFinding> validationFindings,
                                              List<ImportPreviewProcessingError> processingErrors,
                                              long filesSeen,
                                              long itemsDispatched,
                                              long skipped,
                                              long processorFailures,
                                              ImportPreviewClassification classification) {
        return new ImportPreviewResult(ImportPreviewStatus.SUCCESS, validationFindings, processingErrors,
                filesSeen, itemsDispatched, skipped, processorFailures, classification);
    }

    public static ImportPreviewResult empty(List<ImportPreviewFinding> validationFindings,
                                            List<ImportPreviewProcessingError> processingErrors,
                                            long filesSeen,
                                            long skipped,
                                            long processorFailures) {
        return empty(validationFindings, processingErrors, filesSeen, skipped, processorFailures,
                ImportPreviewClassification.empty());
    }

    public static ImportPreviewResult empty(List<ImportPreviewFinding> validationFindings,
                                            List<ImportPreviewProcessingError> processingErrors,
                                            long filesSeen,
                                            long skipped,
                                            long processorFailures,
                                            ImportPreviewClassification classification) {
        return new ImportPreviewResult(ImportPreviewStatus.EMPTY_RESULT, validationFindings, processingErrors,
                filesSeen, 0, skipped, processorFailures, classification);
    }

    public static ImportPreviewResult failure(List<ImportPreviewFinding> validationFindings,
                                              List<ImportPreviewProcessingError> processingErrors,
                                              long filesSeen,
                                              long itemsDispatched,
                                              long skipped,
                                              long processorFailures) {
        return failure(validationFindings, processingErrors, filesSeen, itemsDispatched, skipped,
                processorFailures, ImportPreviewClassification.empty());
    }

    public static ImportPreviewResult failure(List<ImportPreviewFinding> validationFindings,
                                              List<ImportPreviewProcessingError> processingErrors,
                                              long filesSeen,
                                              long itemsDispatched,
                                              long skipped,
                                              long processorFailures,
                                              ImportPreviewClassification classification) {
        return new ImportPreviewResult(ImportPreviewStatus.FAILURE, validationFindings, processingErrors,
                filesSeen, itemsDispatched, skipped, processorFailures, classification);
    }
}
