package org.cttelsamicsterrassa.data.pipeline.core.polling.scope;

import java.util.List;
import java.util.Objects;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchTracking;

/** One poll unit: its identity, stable scope key, ingest filter (with the open rounds) and candidate matches. */
public record PollUnitScope(PollUnit unit, String scopeKey, ScopeFilter filter, List<MatchTracking> candidates) {

    public PollUnitScope {
        Objects.requireNonNull(unit, "unit is required");
        Objects.requireNonNull(scopeKey, "scopeKey is required");
        Objects.requireNonNull(filter, "filter is required");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates is required"));
    }
}
