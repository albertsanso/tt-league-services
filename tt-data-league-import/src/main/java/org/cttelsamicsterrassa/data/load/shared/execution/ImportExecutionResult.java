package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Optional;

public record ImportExecutionResult(ImportSource source, Optional<String> season, ImportProcessStatus status,
                                    ImportExecutionMetrics metrics, List<ImportExecutionIssue> issues,
                                    List<PostProcessingOutcome> postProcessing) {
    public ImportExecutionResult {
        season = season == null ? Optional.empty() : season;
        issues = issues == null ? List.of() : List.copyOf(issues);
        postProcessing = postProcessing == null ? List.of() : List.copyOf(postProcessing);
    }
}
