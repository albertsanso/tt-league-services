package org.cttelsamicsterrassa.data.core.domain.load.model;

import java.util.List;

public record ImportProcessResult(
        ImportProcessStatus status,
        List<ImportPreviewFinding> findings,
        List<ImportPreviewProcessingError> processingErrors,
        long filesSeen,
        long itemsPersisted,
        long skipped,
        long processorFailures,
        long elapsedMillis,
        long persistenceWrites,
        List<String> executionIssues,
        List<String> postProcessingOutcomes) {

    public ImportProcessResult(ImportProcessStatus status, List<ImportPreviewFinding> findings,
                               List<ImportPreviewProcessingError> processingErrors, long filesSeen,
                               long itemsPersisted, long skipped, long processorFailures) {
        this(status, findings, processingErrors, filesSeen, itemsPersisted, skipped, processorFailures,
                0, 0, List.of(), List.of());
    }

    public ImportProcessResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
        processingErrors = processingErrors == null ? List.of() : List.copyOf(processingErrors);
        executionIssues = executionIssues == null ? List.of() : List.copyOf(executionIssues);
        postProcessingOutcomes = postProcessingOutcomes == null ? List.of() : List.copyOf(postProcessingOutcomes);
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
