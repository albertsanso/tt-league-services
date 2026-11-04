package org.cttelsamicsterrassa.data.pipeline.core.tracker;

/** Operational status of a tracked match, mapped from the platform's calendar state. */
public enum TrackedMatchStatus {
    SCHEDULED,
    AWAITING_RESULT,
    REPORTED,
    POSTPONED,
    OVERDUE
}
