package org.cttelsamicsterrassa.data.pipeline.core.execution.port;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ImportJobState(
        UUID importJobId, String status, String errorDetail, List<ImportSeasonState> seasons, String rawJson) {

    private static final Set<String> FINAL = Set.of("SUCCEEDED", "PARTIAL", "FAILED");

    public ImportJobState {
        seasons = seasons == null ? List.of() : List.copyOf(seasons);
    }

    public boolean finished() {
        return FINAL.contains(status);
    }
}
