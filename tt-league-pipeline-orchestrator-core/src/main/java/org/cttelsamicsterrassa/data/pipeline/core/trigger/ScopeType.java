package org.cttelsamicsterrassa.data.pipeline.core.trigger;

/** How a trigger selects what to ingest. */
public enum ScopeType {
    /** The match days still open for the source; resolved by an {@code OpenMatchDayScopeResolver}. */
    OPEN_MATCH_DAYS,
    /** Explicit filters supplied by the caller; single source only. */
    GROUP,
    FULL_SEASON
}
