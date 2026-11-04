package org.cttelsamicsterrassa.data.pipeline.runtime.api;

import java.util.List;
import org.cttelsamicsterrassa.data.pipeline.core.run.ScopeFilter;

/** One scope selection, mirroring the ingest scope contract. */
public record ScopeFilterDto(
        String category, String group, String phase, String territory, String gender, List<Integer> matchDays) {

    public static ScopeFilterDto from(ScopeFilter filter) {
        return new ScopeFilterDto(filter.category(), filter.group(), filter.phase(), filter.territory(),
                filter.gender(), filter.matchDays());
    }

    /** Throws {@link IllegalArgumentException} when the filter is empty or malformed. */
    public ScopeFilter toDomain() {
        return new ScopeFilter(category, group, phase, territory, gender, matchDays);
    }
}
