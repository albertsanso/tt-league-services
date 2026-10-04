package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineRun;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import java.util.List;
import java.util.Objects;

/** Validation shared by {@link TriggerRun.Command} and {@link PendingTrigger}. */
final class TriggerRules {

    private TriggerRules() {
    }

    static void validate(String season, ScopeType scopeType, List<ScopeFilter> filters, String requestedBy) {
        PipelineRun.requireValidSeason(season);
        Objects.requireNonNull(scopeType, "scopeType is required");
        Objects.requireNonNull(filters, "filters is required");
        if (scopeType == ScopeType.GROUP && filters.isEmpty()) {
            throw new IllegalArgumentException("scopeType GROUP requires at least one filter");
        }
        if (scopeType != ScopeType.GROUP && !filters.isEmpty()) {
            throw new IllegalArgumentException("scopeType " + scopeType + " does not accept filters");
        }
        Objects.requireNonNull(requestedBy, "requestedBy is required");
        if (requestedBy.isBlank() || requestedBy.length() > 128) {
            throw new IllegalArgumentException("requestedBy must be 1 to 128 characters");
        }
    }
}
