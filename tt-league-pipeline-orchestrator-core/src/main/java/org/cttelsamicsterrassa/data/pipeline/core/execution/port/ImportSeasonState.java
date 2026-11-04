package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.util.List;

/** One season of a platform import job; {@code counters} is null while the season has no result. */
public record ImportSeasonState(
        String season, String status, String errorDetail, ImportCounters counters, List<String> executionIssues) {

    public ImportSeasonState {
        executionIssues = executionIssues == null ? List.of() : List.copyOf(executionIssues);
    }
}
