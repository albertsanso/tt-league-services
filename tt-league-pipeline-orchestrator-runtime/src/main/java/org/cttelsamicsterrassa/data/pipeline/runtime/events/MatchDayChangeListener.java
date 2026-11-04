package org.cttelsamicsterrassa.data.pipeline.runtime.events;

import java.util.UUID;
import org.cttelsamicsterrassa.data.pipeline.core.run.PipelineSource;

/**
 * Told when tracked match days changed, so live views can refetch. Implementations must not throw into the caller;
 * callers still log and ignore any failure.
 */
public interface MatchDayChangeListener {

    /** Why match days changed: the tracker recomputed them, or an operator acted on one. */
    enum Cause {
        RECOMPUTED,
        ACTION
    }

    /** {@code matchDayId} is null after a recompute, which can touch any match day of the source and season. */
    void matchDaysChanged(PipelineSource source, String season, UUID matchDayId, Cause cause);
}
