package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/** What one recompute changed. {@code skippedGroups} counts open jornadas without a competition. */
public record RecomputeOutcome(
        PipelineSource source,
        String season,
        int created,
        int updated,
        int opened,
        int closed,
        int reopened,
        int reported,
        int skippedGroups) {
}
