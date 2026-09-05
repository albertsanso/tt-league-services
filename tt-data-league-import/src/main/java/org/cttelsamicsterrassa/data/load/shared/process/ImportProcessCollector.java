package org.cttelsamicsterrassa.data.load.shared.process;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewFinding;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportPreviewProcessingError;
import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessResult;
import java.util.ArrayList;
import java.util.List;

public class ImportProcessCollector {
    private final List<ImportPreviewFinding> findings = new ArrayList<>();
    private final List<ImportPreviewProcessingError> errors = new ArrayList<>();

    public void error(String message, String location) {
        errors.add(new ImportPreviewProcessingError(message, location));
    }

    public ImportProcessResult toResult(long filesSeen, long itemsPersisted, long skipped,
                                        long processorFailures) {
        List<ImportPreviewFinding> summary = new ArrayList<>(findings);
        summary.add(new ImportPreviewFinding("info",
                "Import scanned %d files and persisted %d item(s).".formatted(filesSeen, itemsPersisted), null));
        if (skipped > 0) {
            summary.add(new ImportPreviewFinding("warning",
                    "Traversal skipped %d file or fixture item(s).".formatted(skipped), null));
        }
        if (!errors.isEmpty() || processorFailures > 0) {
            return ImportProcessResult.failure(summary, errors, filesSeen, itemsPersisted, skipped, processorFailures);
        }
        if (itemsPersisted == 0) {
            return ImportProcessResult.empty(summary, errors, filesSeen, skipped, processorFailures);
        }
        return ImportProcessResult.success(summary, errors, filesSeen, itemsPersisted, skipped, processorFailures);
    }
}
