package org.cttelsamicsterrassa.data.load.shared.preview;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewFinding;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewResult;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewStatus;

import java.util.ArrayList;
import java.util.List;

public class ImportPreviewCollector {

    private final List<ImportPreviewFinding> findings = new ArrayList<>();
    private final List<ImportPreviewProcessingError> errors = new ArrayList<>();

    public void info(String message, String location) {
        findings.add(new ImportPreviewFinding("info", message, location));
    }

    public void warning(String message, String location) {
        findings.add(new ImportPreviewFinding("warning", message, location));
    }

    public void error(String message, String location) {
        errors.add(new ImportPreviewProcessingError(message, location));
    }

    public ImportPreviewResult toResult(long filesSeen, long itemsDispatched, long skipped, long processorFailures) {
        ImportPreviewStatus status;
        if (!errors.isEmpty() || processorFailures > 0) {
            status = ImportPreviewStatus.FAILURE;
        } else if (itemsDispatched == 0) {
            status = ImportPreviewStatus.EMPTY_RESULT;
        } else {
            status = ImportPreviewStatus.SUCCESS;
        }
        List<ImportPreviewFinding> summary = new ArrayList<>(findings);
        summary.addFirst(new ImportPreviewFinding(
                "info",
                "Preview scanned %d files and validated %d match report item(s)."
                        .formatted(filesSeen, itemsDispatched),
                null));
        if (skipped > 0) {
            summary.add(new ImportPreviewFinding(
                    "warning",
                    "Traversal skipped %d file or fixture item(s).".formatted(skipped),
                    null));
        }
        return new ImportPreviewResult(status, summary, errors, filesSeen, itemsDispatched, skipped, processorFailures);
    }
}
