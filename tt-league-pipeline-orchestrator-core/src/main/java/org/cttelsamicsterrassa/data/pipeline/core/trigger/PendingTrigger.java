package org.cttelsamicsterrassa.data.pipeline.core.trigger;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A trigger waiting for the source's active run to end. It stores the request (scope type and filters), not the
 * resolved scope: {@link ScopeType#OPEN_MATCH_DAYS} is resolved when it launches.
 */
public record PendingTrigger(
        PipelineSource source,
        String season,
        ScopeType scopeType,
        List<ScopeFilter> filters,
        boolean force,
        String requestedBy,
        Instant requestedAt) {

    public PendingTrigger {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(filters, "filters is required");
        filters = List.copyOf(filters);
        TriggerRules.validate(season, scopeType, filters, requestedBy);
        Objects.requireNonNull(requestedAt, "requestedAt is required");
    }
}
