package org.cttelsamicsterrassa.data.core.domain.consolidation.model;

/**
 * Which side of a {@link ConsolidationAction} a club snapshot belongs to.
 */
public enum ConsolidationActionClubRole {
    /** A club that was consumed by the action and no longer exists. */
    SOURCE,
    /** A club that survived the action. */
    TARGET
}
