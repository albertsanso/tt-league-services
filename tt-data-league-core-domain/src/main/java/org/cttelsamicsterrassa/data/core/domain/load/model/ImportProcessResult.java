package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.List;

public record ImportProcessResult(
        ImportProcessStatus status,
        List<ImportPreviewFinding> findings,
        List<ImportPreviewProcessingError> processingErrors,
        long filesSeen,
        long itemsPersisted,
        long skipped,
        long processorFailures) {

    public ImportProcessResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        processingErrors = processingErrors == null ? List.of() : List.copyOf(processingErrors);
    }

    public static ImportProcessResult success(List<ImportPreviewFinding> findings,
                                              List<ImportPreviewProcessingError> errors,
                                              long filesSeen, long itemsPersisted, long skipped,
                                              long processorFailures) {
        return new ImportProcessResult(ImportProcessStatus.SUCCESS, findings, errors, filesSeen,
                itemsPersisted, skipped, processorFailures);
    }

    public static ImportProcessResult empty(List<ImportPreviewFinding> findings,
                                            List<ImportPreviewProcessingError> errors,
                                            long filesSeen, long skipped, long processorFailures) {
        return new ImportProcessResult(ImportProcessStatus.EMPTY_RESULT, findings, errors, filesSeen,
                0, skipped, processorFailures);
    }

    public static ImportProcessResult failure(List<ImportPreviewFinding> findings,
                                              List<ImportPreviewProcessingError> errors,
                                              long filesSeen, long itemsPersisted, long skipped,
                                              long processorFailures) {
        return new ImportProcessResult(ImportProcessStatus.FAILURE, findings, errors, filesSeen,
                itemsPersisted, skipped, processorFailures);
    }
}
