package org.cttelsamicsterrassa.data.load.shared.execution;

import org.cttelsamicsterrassa.data.core.domain.load.model.ImportProcessStatus;
import org.cttelsamicsterrassa.data.core.domain.match.model.RoundProgress;
import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;
import java.util.Optional;

public record ImportExecutionResult(ImportSource source, Optional<String> season, ImportProcessStatus status,
                                    ImportExecutionMetrics metrics, List<ImportExecutionIssue> issues,
                                    List<PostProcessingOutcome> postProcessing,
                                    List<ImportExecutionIssue> warnings,
                                    List<RoundProgress> roundProgress) {
    public ImportExecutionResult(ImportSource source, Optional<String> season, ImportProcessStatus status,
                                 ImportExecutionMetrics metrics, List<ImportExecutionIssue> issues,
                                 List<PostProcessingOutcome> postProcessing) {
        this(source, season, status, metrics, issues, postProcessing, List.of(), List.of());
    }

    public ImportExecutionResult(ImportSource source, Optional<String> season, ImportProcessStatus status,
                                 ImportExecutionMetrics metrics, List<ImportExecutionIssue> issues,
                                 List<PostProcessingOutcome> postProcessing, List<ImportExecutionIssue> warnings) {
        this(source, season, status, metrics, issues, postProcessing, warnings, List.of());
    }

    public ImportExecutionResult {
        season = season == null ? Optional.empty() : season;
        issues = issues == null ? List.of() : List.copyOf(issues);
        postProcessing = postProcessing == null ? List.of() : List.copyOf(postProcessing);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        roundProgress = roundProgress == null ? List.of() : List.copyOf(roundProgress);
    }

    /**
     * The jornada progress (FEAT-00084) is deliberately left out: callers that surface it do so with
     * one dedicated line per row, so printing it here too would show the same rows twice in the run
     * log. The record accessors still expose the list in full.
     */
    @Override
    public String toString() {
        return "ImportExecutionResult[source=%s, season=%s, status=%s, metrics=%s, issues=%s, "
                        .formatted(source, season, status, metrics, issues)
                + "postProcessing=%s, warnings=%s]".formatted(postProcessing, warnings);
    }
}
