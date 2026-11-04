package org.cttelsamicsterrassa.data.pipeline.core.tracker;

import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/**
 * What one recompute touched. {@code skippedGroups} counts open jornadas without a competition; {@code changed}
 * counts match days and matches whose window, status, date, teams or membership differ from what was stored.
 */
public record RecomputeOutcome(
        PipelineSource source,
        String season,
        int created,
        int updated,
        int opened,
        int closed,
        int reopened,
        int reported,
        int skippedGroups,
        int changed) {

    /**
     * True when the recompute created or visibly changed any match day or match: new days, lifecycle changes, newly
     * reported matches, or a changed window, status, date, team or membership. A no-op recompute only refreshes
     * timestamps and is not a change.
     */
    public boolean hasChanges() {
        return created + opened + closed + reopened + reported + changed > 0;
    }
}
