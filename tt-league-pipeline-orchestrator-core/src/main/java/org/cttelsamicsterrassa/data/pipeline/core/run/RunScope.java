package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.util.List;

/** Scope of a run; no filters means the whole season. */
public record RunScope(List<ScopeFilter> filters) {

    public RunScope {
        Checks.required(filters, "filters");
        filters = filters.stream().map(f -> Checks.required(f, "filter")).distinct().toList();
    }

    public static RunScope fullSeason() {
        return new RunScope(List.of());
    }

    public boolean isFullSeason() {
        return filters.isEmpty();
    }
}
