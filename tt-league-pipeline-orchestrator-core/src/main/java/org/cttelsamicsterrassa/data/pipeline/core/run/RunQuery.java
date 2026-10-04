package org.cttelsamicsterrassa.data.pipeline.core.run;

import java.time.Instant;
import java.util.Set;

/**
 * Filter and page of a run listing. Empty sets mean "any"; {@code createdFrom} is inclusive and {@code createdTo}
 * exclusive, both optional.
 */
public record RunQuery(
        Set<PipelineSource> sources,
        Set<RunStatus> statuses,
        Instant createdFrom,
        Instant createdTo,
        int page,
        int size) {

    public static final int MAX_SIZE = 100;

    public RunQuery {
        sources = Set.copyOf(Checks.required(sources, "sources"));
        statuses = Set.copyOf(Checks.required(statuses, "statuses"));
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }
}
